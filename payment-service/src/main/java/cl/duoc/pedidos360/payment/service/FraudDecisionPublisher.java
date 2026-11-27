package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class FraudDecisionPublisher {
 private final JdbcTemplate db;
 private final KafkaTemplate<String,String> kafka;
 private final ObjectMapper json;
 public FraudDecisionPublisher(JdbcTemplate db,KafkaTemplate<String,String> kafka,ObjectMapper json) {
   this.db=db;this.kafka=kafka;this.json=json;
 }
 @Scheduled(fixedDelay=250,initialDelay=1000)
 public void publish(){
   try {
     var batch=db.queryForList("SELECT payment_id,decision,reason FROM payments.fraud_decisions " +
               "WHERE published_at IS NULL ORDER BY created_at,payment_id LIMIT 25");
     for(var row:batch){
       UUID id=(UUID)row.get("payment_id");
       String payload=json.writeValueAsString(Map.of("eventId",id.toString(),
           "type","payments.fraud.decision","paymentId",id.toString(),
           "decision",row.get("decision"),"reason",row.get("reason")));
       kafka.send("payments.fraud.decisions",id.toString(),payload).get(5,TimeUnit.SECONDS);
       db.update("UPDATE payments.fraud_decisions SET published_at=NOW() WHERE payment_id=?",id);
     }
   }catch(Exception ex){
     org.slf4j.LoggerFactory.getLogger(getClass()).warn("Pending fraud Kafka decisions: {}",ex.toString());
   }
 }
}
