package cl.duoc.pedidos360.payment.service;

import cl.duoc.pedidos360.payment.api.PaymentController.Checkout;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
public class PaymentService {
 private final JdbcTemplate db; private final ObjectMapper json; private final TransactionTemplate tx;
 private final RestClient client; private final WebpayClient webpay; private final FraudGateway fraud;
 private final String ordersUrl, inventoryUrl, internalToken, hmacKey;
 private final boolean simulationEnabled;
 private final String frontendUrl;

 public PaymentService(JdbcTemplate db,ObjectMapper json,TransactionTemplate tx,WebpayClient webpay,FraudGateway fraud,
   @Value("${payment.orders-url}") String ordersUrl,
   @Value("${payment.inventory-url}") String inventoryUrl,
   @Value("${payment.internal-token}") String internalToken,
   @Value("${payment.antifraud-hmac-key}") String hmacKey,
   @Value("${payment.simulation-enabled:false}") boolean simulationEnabled,
   @Value("${payment.frontend-url:http://localhost}") String frontendUrl) {
    this.db=db;this.json=json;this.tx=tx;this.webpay=webpay;this.fraud=fraud;
    this.ordersUrl=ordersUrl;this.inventoryUrl=inventoryUrl;this.internalToken=internalToken;
    this.hmacKey=hmacKey;this.simulationEnabled=simulationEnabled;this.frontendUrl=frontendUrl;
    this.client=RestClient.builder().build();
 }
 private static void checkKey(String key){
   if(key==null||!key.matches("[A-Za-z0-9_-]{8,80}"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key inválida");
 }
 private String fingerprint(String card){
    if(card==null || !card.matches("[A-Za-z0-9_-]{4,64}") || hmacKey.length()<16)
       throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Referencia de tarjeta simulada o secreto antifraude inválido");
    try{
      Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(card.getBytes(StandardCharsets.UTF_8)));
    }catch(Exception ex){throw new IllegalStateException("Error HMAC",ex);}
 }
 private JsonNode fetchOrder(UUID orderId,String bearer,String demo){
     var req=client.get().uri(ordersUrl+"/api/orders/"+orderId);
     if(bearer!=null && !bearer.isBlank()) req.header("Authorization",bearer);
     if(demo!=null && !demo.isBlank()) req.header("X-Demo-User",demo);
     try{return req.retrieve().body(JsonNode.class);}
     catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No se pudo consultar la orden autenticada",ex);}
 }
 private Map<String,Object> asResponse(UUID id){
    return db.queryForObject("SELECT id,order_id,method,status,amount,webpay_token,created_at FROM payments.payment_attempts WHERE id=?",
        (rs,i)->{
          Map<String,Object> out=new LinkedHashMap<>();out.put("id",rs.getObject("id"));
          out.put("orderId",rs.getObject("order_id"));out.put("method",rs.getString("method"));
          out.put("status",rs.getString("status"));out.put("amount",rs.getBigDecimal("amount"));
          if(rs.getString("webpay_token")!=null)out.put("webpayToken",rs.getString("webpay_token"));
          return out;
        },id);
 }
 public Map<String,Object> get(UUID id,String bearer,String demo){
    Map<String,Object> payment=asResponse(id);
    fetchOrder((UUID)payment.get("orderId"),bearer,demo); // enforce same user via Orders.
    return payment;
 }

 public Map<String,Object> checkout(Checkout payload,String key,String bearer,String demo){
    checkKey(key);
    if(internalToken.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Secreto interno no configurado");
    JsonNode order=fetchOrder(payload.orderId(),bearer,demo);
    if(!"PENDING_PAYMENT".equals(order.path("status").asText()))
       throw new ResponseStatusException(HttpStatus.CONFLICT,"La orden no admite pagos");
    BigDecimal amount=new BigDecimal(order.path("totalAmount").asText());
    if(amount.signum()<=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Monto inválido");
    String cardFingerprint=null;
    if("SIMULATED".equals(payload.method())) {
       if(!simulationEnabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Simulación deshabilitada");
       cardFingerprint=fingerprint(payload.cardReference());
    }
    final String fp=cardFingerprint;
    UUID id=tx.execute(status->{
       // Repeated client retries must resolve to a single payment attempt.
       var same=db.query("SELECT id FROM payments.payment_attempts WHERE order_id=? AND request_key=?",
                (rs,i)->(UUID)rs.getObject(1),payload.orderId(),key);
       if(!same.isEmpty()) return same.get(0);
       UUID newid=UUID.randomUUID();
       // Simulated cards must be approved by the Kafka fraud decision consumer.
       String initialStatus=fp==null?"CREATED":"FRAUD_PENDING";
       db.update("INSERT INTO payments.payment_attempts(id,order_id,request_key,card_fingerprint,method,status,amount) " +
                 "VALUES (?,?,?,?,?,?,?)",newid,payload.orderId(),key,fp,payload.method(),initialStatus,amount);
       return newid;
    });
    Map<String,Object> existing=asResponse(id);
    if("FRAUD_PENDING".equals(existing.get("status"))) {
        // Fail closed until Kafka evaluates this fingerprint and applies the decision.
        fraud.evaluate(id,fp);
        existing=asResponse(id);
    }
    if("BLOCKED".equals(existing.get("status")))
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Tarjeta bloqueada por antifraude");
    if(!"CREATED".equals(existing.get("status"))) return existing;
    // Reserve inventory before initiating external payments.
    try {
       Map<String,Object> reserve=Map.of("orderId",payload.orderId().toString(),"quote",json.convertValue(order.path("quote"),Map.class));
       client.post().uri(inventoryUrl+"/internal/inventory/reserve")
            .header("X-Service-Token",internalToken).contentType(MediaType.APPLICATION_JSON)
            .body(reserve).retrieve().toBodilessEntity();
    } catch(Exception ex){
       db.update("UPDATE payments.payment_attempts SET status='RESERVE_FAILED',updated_at=NOW() WHERE id=?",id);
       throw new ResponseStatusException(HttpStatus.CONFLICT,"No se pudo reservar inventario",ex);
    }
    if("SIMULATED".equals(payload.method())){
       boolean approved=Boolean.TRUE.equals(payload.approved());
       settle(id,approved,null);
       return asResponse(id);
    }
    try {
       var session=webpay.create(payload.orderId(),id,amount);
       db.update("UPDATE payments.payment_attempts SET status='REDIRECT_PENDING',webpay_token=?,updated_at=NOW() WHERE id=?",
              session.token(),id);
       var response=asResponse(id);
       response.put("redirectUrl",session.url());
       return response;
    } catch(Exception ex){
       settle(id,false,null);
       throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Webpay no pudo inicializar la transacción",ex);
    }
 }
 private void settle(UUID id, boolean approved,String authCode){
   tx.executeWithoutResult(status->{
     Map<String,Object> p=asResponse(id);
     if(List.of("APPROVED","REJECTED").contains(p.get("status"))) return;
     String newStatus=approved?"APPROVED":"REJECTED";
     db.update("UPDATE payments.payment_attempts SET status=?, authorization_code=?,updated_at=NOW() WHERE id=?",newStatus,authCode,id);
     String type=approved?"payment.approved":"payment.rejected";
     UUID eventId=UUID.randomUUID();
     try {
       String payload=json.writeValueAsString(Map.of("eventId",eventId.toString(),"type",type,
          "orderId",p.get("orderId").toString(),"paymentId",id.toString(),"status",newStatus));
       db.update("INSERT INTO payments.payment_events(id,routing_key,body) VALUES (?,?,?)",eventId,type,payload);
     } catch(Exception ex){throw new IllegalStateException("Evento no serializable",ex);}
   });
 }
 public boolean commitWebpay(String token,String cancelled){
   if((token==null||token.isBlank()) && cancelled==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sin token de Webpay");
   String actual=token==null?cancelled:token;
   List<UUID> ids=db.query("SELECT id FROM payments.payment_attempts WHERE webpay_token=?",(rs,i)->(UUID)rs.getObject(1),actual);
   if(ids.size()!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Transacción no encontrada");
   UUID id=ids.get(0); boolean approved=false;String code=null;
   if(token!=null && !token.isBlank()) {
      JsonNode response=webpay.commit(token);
      BigDecimal expected=(BigDecimal)asResponse(id).get("amount");
      BigDecimal actualAmount=new BigDecimal(response.path("amount").asText("-1"));
      approved="AUTHORIZED".equals(response.path("status").asText()) &&
        response.path("response_code").asInt(-1)==0 && expected.compareTo(actualAmount)==0;
      code=response.path("authorization_code").asText(null);
   }
   settle(id,approved,code);
   return approved;
 }
 @org.springframework.scheduling.annotation.Scheduled(fixedDelay=60000,initialDelay=120000)
 public void expireAbandoned() {
    try {
      var abandoned=db.query("SELECT id FROM payments.payment_attempts WHERE status IN ('CREATED','REDIRECT_PENDING') " +
         "AND created_at < NOW() - INTERVAL '30 minutes' ORDER BY created_at LIMIT 20",
         (rs,i)->(UUID)rs.getObject(1));
      for(UUID id:abandoned) settle(id,false,null);
    } catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("No se pudo expirar pagos pendientes",ex);}
 }
 public String resultRedirect(boolean approved){
   return frontendUrl.replaceAll("/+$","") + "/?payment=" + (approved?"approved":"rejected");
 }
}
