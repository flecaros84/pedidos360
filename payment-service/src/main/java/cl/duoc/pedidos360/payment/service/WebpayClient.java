package cl.duoc.pedidos360.payment.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;
@Component
public class WebpayClient {
  private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
  private final ObjectMapper mapper;private final String api,code,key,returnUrl;
  public record Session(String token,String url){}
  public WebpayClient(ObjectMapper mapper,
    @Value("${payment.webpay.api-base}") String api,
    @Value("${payment.webpay.commerce-code}") String code,
    @Value("${payment.webpay.api-key}") String key,
    @Value("${payment.webpay.return-url}") String returnUrl){
     this.mapper=mapper;this.api=api;this.code=code;this.key=key;this.returnUrl=returnUrl;
  }
  private HttpRequest.Builder request(String uri){
     if(code.isBlank()||key.isBlank()||returnUrl.isBlank())
        throw new IllegalStateException("Credenciales Webpay no configuradas");
     return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(20))
        .header("Tbk-Api-Key-Id",code).header("Tbk-Api-Key-Secret",key).header("Content-Type","application/json");
  }
  private JsonNode send(HttpRequest request){
    try {
      var response=client.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()<200||response.statusCode()>299)
         throw new IllegalStateException("Error Webpay HTTP "+response.statusCode());
      return mapper.readTree(response.body());
    } catch(Exception ex){throw new IllegalStateException("Webpay no disponible",ex);}
  }
  public Session create(UUID orderId,UUID paymentId,BigDecimal amount){
      try {
       String body=mapper.writeValueAsString(Map.of("buy_order",orderId.toString().substring(0,26),
        "session_id",paymentId.toString(),"amount",amount,"return_url",returnUrl));
       var r=send(request(api+"/transactions").POST(HttpRequest.BodyPublishers.ofString(body)).build());
       return new Session(r.path("token").asText(),r.path("url").asText());
      } catch(Exception ex){throw new IllegalStateException("No se pudo crear la sesión Webpay",ex);}
  }
  public JsonNode commit(String token){
      return send(request(api+"/transactions/"+java.net.URLEncoder.encode(token,java.nio.charset.StandardCharsets.UTF_8))
          .PUT(HttpRequest.BodyPublishers.noBody()).build());
  }
}
