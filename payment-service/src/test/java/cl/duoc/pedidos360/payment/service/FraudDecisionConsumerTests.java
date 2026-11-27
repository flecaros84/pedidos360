package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudDecisionConsumerTests {
 @Test void allowedDecisionCannotOverridePersistentlyBlockedCard()throws Exception {
   UUID id=UUID.randomUUID();
   JdbcTemplate db=mock(JdbcTemplate.class);
   when(db.queryForMap(startsWith("SELECT status"),eq(id))).thenReturn(
      Map.of("status","FRAUD_PENDING","card_fingerprint","a".repeat(64),"order_id",UUID.randomUUID()));
   when(db.queryForObject(startsWith("SELECT COUNT(*)"),eq(Integer.class),eq("a".repeat(64)))).thenReturn(1);
   when(db.update(startsWith("UPDATE payments.payment_attempts"),eq("BLOCKED"),eq(id))).thenReturn(1);
   String raw=new ObjectMapper().writeValueAsString(Map.of("type","payments.fraud.decision",
      "paymentId",id.toString(),"decision","ALLOW","reason","ALLOW"));
   new FraudDecisionConsumer(db,new ObjectMapper()).apply(raw);
   verify(db).update(startsWith("UPDATE payments.payment_attempts"),eq("BLOCKED"),eq(id));
   verify(db).update(startsWith("INSERT INTO payments.fraud_alerts"),any(UUID.class),eq("a".repeat(64)),eq("CARD_BLOCKED"));
 }
 @Test void idempotentReplayDoesNotCreateDuplicateAlert()throws Exception {
   UUID id=UUID.randomUUID();
   JdbcTemplate db=mock(JdbcTemplate.class);
   when(db.queryForMap(startsWith("SELECT status"),eq(id))).thenReturn(
      Map.of("status","BLOCKED","card_fingerprint","a".repeat(64),"order_id",UUID.randomUUID()));
   String raw=new ObjectMapper().writeValueAsString(Map.of("type","payments.fraud.decision",
      "paymentId",id.toString(),"decision","BLOCK","reason","CARD_BLOCKED"));
   new FraudDecisionConsumer(db,new ObjectMapper()).apply(raw);
   verify(db,never()).update(startsWith("INSERT INTO payments.fraud_alerts"),any(),any(),any());
 }
}
