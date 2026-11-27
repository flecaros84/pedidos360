package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FraudGateway {
 private final JdbcTemplate db;
 private final KafkaTemplate<String,String> kafka;
 private final ObjectMapper json;
 private final long timeoutMs;
 public FraudGateway(JdbcTemplate db,KafkaTemplate<String,String> kafka,ObjectMapper json,
         @Value("${payment.fraud.timeout-ms:12000}") long timeoutMs) {
   this.db=db;this.kafka=kafka;this.json=json;this.timeoutMs=timeoutMs;
 }
 public void evaluate(UUID paymentId,String fingerprint){
   if(fingerprint==null||fingerprint.isBlank())
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Huella antifraude no disponible");
   // The raw card reference is never published; only its HMAC fingerprint.
   try {
     String raw=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),
        "type","payments.fraud.request","paymentId",paymentId.toString(),
        "cardFingerprint",fingerprint));
     kafka.send("payments.fraud.requests",fingerprint,raw).get(5,TimeUnit.SECONDS);
   } catch(Exception ex) {
     throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
       "Kafka antifraude no disponible: pago no autorizado",ex);
   }
   long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);
   while(System.nanoTime()<deadline) {
     String status=db.queryForObject("SELECT status FROM payments.payment_attempts WHERE id=?",
                                     String.class,paymentId);
     if(!"FRAUD_PENDING".equals(status)) return;
     try { Thread.sleep(120); }
     catch(InterruptedException ex) {
       Thread.currentThread().interrupt();
       throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Evaluación antifraude interrumpida",ex);
     }
   }
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
           "Evaluación antifraude pendiente; reintentar con la misma Idempotency-Key");
 }
}
