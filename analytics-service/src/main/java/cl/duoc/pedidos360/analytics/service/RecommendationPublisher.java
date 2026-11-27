package cl.duoc.pedidos360.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RecommendationPublisher {
 private final JdbcTemplate db;
 private final KafkaTemplate<String,String> kafka;
 private final ObjectMapper json;

 public RecommendationPublisher(JdbcTemplate db,KafkaTemplate<String,String> kafka,ObjectMapper json) {
   this.db=db;this.kafka=kafka;this.json=json;
 }
 @Scheduled(fixedDelay=2000,initialDelay=10000)
 public void publish() {
   try {
     var batch=db.queryForList("SELECT event_id,user_id FROM analytics.recommendation_requests " +
                "WHERE published_at IS NULL ORDER BY created_at LIMIT 50");
     for(var row:batch) {
       UUID id=(UUID)row.get("event_id");
       String user=(String)row.get("user_id");
       String payload=json.writeValueAsString(Map.of("eventId",id.toString(),"userId",user,
                                                      "type","recommendations.recompute"));
       kafka.send("recommendations.updated",user,payload).get(5,TimeUnit.SECONDS);
       db.update("UPDATE analytics.recommendation_requests SET published_at=NOW() WHERE event_id=?",id);
     }
   }catch(Exception ex) {
     org.slf4j.LoggerFactory.getLogger(getClass()).warn("Pending Kafka recommendations: {}",ex.toString());
   }
 }
}
