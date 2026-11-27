package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class FraudRequestConsumerTests {
 private final ObjectMapper json=new ObjectMapper();
 private static final String FP="a".repeat(64);

 private String request(UUID id)throws Exception {
   return json.writeValueAsString(Map.of("type","payments.fraud.request",
     "paymentId",id.toString(),"cardFingerprint",FP));
 }
 private JdbcTemplate db(UUID id,int recent,int alreadyBlocked) {
   JdbcTemplate db=mock(JdbcTemplate.class);
   when(db.queryForObject(startsWith("SELECT card_fingerprint"),eq(String.class),eq(id))).thenReturn(FP);
   when(db.queryForObject(startsWith("SELECT COUNT(*) FROM payments.fraud_decisions"),eq(Integer.class),eq(id))).thenReturn(0);
   when(db.queryForObject(startsWith("SELECT status"),eq(String.class),eq(id))).thenReturn("FRAUD_PENDING");
   when(db.queryForObject(startsWith("SELECT COUNT(*) FROM payments.fraud_blocked_cards"),eq(Integer.class),eq(FP))).thenReturn(alreadyBlocked);
   when(db.queryForObject(contains("JOIN payments.payment_attempts"),eq(Integer.class),eq(id),eq(FP),eq(id))).thenReturn(recent);
   return db;
 }
 @Test void fourthAttemptBlocksCardAndCreatesDecision()throws Exception {
   UUID id=UUID.randomUUID();
   JdbcTemplate db=db(id,3,0);
   new FraudRequestConsumer(db,json).check(request(id));
   verify(db).update(startsWith("INSERT INTO payments.fraud_blocked_cards"),eq(FP),eq("MORE_THAN_3_IN_10_SECONDS"));
   verify(db).update(startsWith("INSERT INTO payments.fraud_decisions"),
       eq(id),eq(FP),eq("BLOCK"),eq("MORE_THAN_3_IN_10_SECONDS"));
 }
 @Test void earlierAttemptIsAllowed()throws Exception {
   UUID id=UUID.randomUUID();JdbcTemplate db=db(id,2,0);
   new FraudRequestConsumer(db,json).check(request(id));
   verify(db,never()).update(startsWith("INSERT INTO payments.fraud_blocked_cards"),any(),any());
   verify(db).update(startsWith("INSERT INTO payments.fraud_decisions"),eq(id),eq(FP),eq("ALLOW"),eq("ALLOW"));
 }
 @Test void blockedCardStaysBlockedAfterTenSeconds()throws Exception {
   UUID id=UUID.randomUUID();JdbcTemplate db=db(id,0,1);
   new FraudRequestConsumer(db,json).check(request(id));
   verify(db).update(startsWith("INSERT INTO payments.fraud_decisions"),eq(id),eq(FP),eq("BLOCK"),eq("CARD_BLOCKED"));
 }
}
