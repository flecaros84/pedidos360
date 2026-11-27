package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;

@Component
public class FraudDecisionConsumer {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 public FraudDecisionConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}

 @Transactional
 @KafkaListener(topics="payments.fraud.decisions",groupId="fraud-actions")
 public void apply(String raw)throws Exception {
   JsonNode event=json.readTree(raw);
   if(!"payments.fraud.decision".equals(event.path("type").asText()))return;
   UUID paymentId=UUID.fromString(event.path("paymentId").asText());
   String decision=event.path("decision").asText("");
   if(!decision.equals("ALLOW")&&!decision.equals("BLOCK"))throw new IllegalArgumentException("Invalid fraud decision");
   var payment=db.queryForMap("SELECT status,order_id,card_fingerprint FROM payments.payment_attempts WHERE id=?",paymentId);
   if(!"FRAUD_PENDING".equals(payment.get("status")))return;
   String fp=(String)payment.get("card_fingerprint");
   Integer blocked=db.queryForObject("SELECT COUNT(*) FROM payments.fraud_blocked_cards WHERE card_fingerprint=?",
                                     Integer.class,fp);
   // A card blocked by a later attempt must never be approved by an older pending decision.
   boolean block=decision.equals("BLOCK")||(blocked!=null&&blocked>0);
   String reason=decision.equals("BLOCK")?event.path("reason").asText("CARD_BLOCKED"):"CARD_BLOCKED";
   int changed=db.update("UPDATE payments.payment_attempts SET status=?,updated_at=NOW() " +
             "WHERE id=? AND status='FRAUD_PENDING'",block?"BLOCKED":"CREATED",paymentId);
   if(changed!=1||!block)return;
   db.update("INSERT INTO payments.fraud_alerts(id,card_fingerprint,reason) VALUES (?,?,?)",
             UUID.randomUUID(),fp,reason);
   UUID eventId=UUID.randomUUID();
   String payload=json.writeValueAsString(Map.of("eventId",eventId.toString(),
            "type","payment.blocked","orderId",payment.get("order_id").toString(),
            "paymentId",paymentId.toString(),"status","BLOCKED","reason",reason));
   // Existing Rabbit consumers stay independent; a separate publisher sends audit.events to Kafka.
   db.update("INSERT INTO payments.payment_events(id,routing_key,body) VALUES (?,?,?)",
             eventId,"payment.blocked",payload);
 }
}
