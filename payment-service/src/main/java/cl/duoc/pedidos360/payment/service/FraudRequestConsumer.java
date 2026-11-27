package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class FraudRequestConsumer {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 public FraudRequestConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}

 @Transactional
 @KafkaListener(topics="payments.fraud.requests",groupId="fraud-engine")
 public void check(String raw)throws Exception {
   JsonNode request=json.readTree(raw);
   if(!"payments.fraud.request".equals(request.path("type").asText())) return;
   UUID paymentId=UUID.fromString(request.path("paymentId").asText());
   String fp=request.path("cardFingerprint").asText("");
   if(!fp.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid fraud fingerprint");
   // Serialize all evaluations for one card, even with concurrent HTTP requests.
   db.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))",fp);
   String stored=db.queryForObject("SELECT card_fingerprint FROM payments.payment_attempts WHERE id=?",
                                   String.class,paymentId);
   if(!fp.equals(stored))throw new IllegalArgumentException("Fraud fingerprint mismatch");
   Integer priorDecision=db.queryForObject("SELECT COUNT(*) FROM payments.fraud_decisions WHERE payment_id=?",
                                            Integer.class,paymentId);
   if(priorDecision!=null && priorDecision>0)return;
   String status=db.queryForObject("SELECT status FROM payments.payment_attempts WHERE id=?",
                                   String.class,paymentId);
   if(!"FRAUD_PENDING".equals(status))return;
   Integer blocked=db.queryForObject("SELECT COUNT(*) FROM payments.fraud_blocked_cards WHERE card_fingerprint=?",
                                     Integer.class,fp);
   String reason="ALLOW";
   if(blocked!=null&&blocked>0) {
     reason="CARD_BLOCKED";
   } else {
     // Count earlier requests within the same ten-second window. This uses the
     // payment creation time, not consumer execution time, so Kafka lag is safe.
     Integer recent=db.queryForObject(
       "SELECT COUNT(*) FROM payments.payment_attempts p " +
       "JOIN payments.payment_attempts current_payment ON current_payment.id=? " +
       "WHERE p.card_fingerprint=? AND p.id<>? " +
       "AND p.created_at >= current_payment.created_at-INTERVAL '10 seconds' " +
       "AND (p.created_at < current_payment.created_at OR " +
       "(p.created_at=current_payment.created_at AND p.id::text<current_payment.id::text))",
       Integer.class,paymentId,fp,paymentId);
     if(recent!=null&&recent>=3) {
       reason="MORE_THAN_3_IN_10_SECONDS";
       db.update("INSERT INTO payments.fraud_blocked_cards(card_fingerprint,reason) VALUES (?,?) " +
                 "ON CONFLICT(card_fingerprint) DO NOTHING",fp,reason);
     }
   }
   String decision="ALLOW".equals(reason)?"ALLOW":"BLOCK";
   db.update("INSERT INTO payments.fraud_decisions(payment_id,card_fingerprint,decision,reason) " +
             "VALUES (?,?,?,?) ON CONFLICT(payment_id) DO NOTHING",paymentId,fp,decision,reason);
 }
}
