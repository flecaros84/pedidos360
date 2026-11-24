package cl.duoc.pedidos360.cart.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@Component
public class InteractionPublisher {
 private final KafkaTemplate<String,String> kafka;private final ObjectMapper json;
 public InteractionPublisher(KafkaTemplate<String,String> kafka,ObjectMapper json){this.kafka=kafka;this.json=json;}
 public void publish(String type,String userId,String sku,String category){
   try {
     String event=json.writeValueAsString(Map.of("eventId",UUID.randomUUID().toString(),
       "type",type,"userId",userId==null?"anonymous":userId,
       "sku",sku==null?"":sku,"category",category==null?"":category,
       "at",Instant.now().toString()));
     kafka.send("shopping.interactions",userId==null?"anonymous":userId,event)
       .whenComplete((result,ex)->{if(ex!=null) org.slf4j.LoggerFactory.getLogger(getClass()).warn("Kafka: {}",ex.toString());});
   }catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Evento de navegación fallido",ex);}
 }
}
