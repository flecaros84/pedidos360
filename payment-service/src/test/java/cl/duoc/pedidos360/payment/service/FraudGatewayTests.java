package cl.duoc.pedidos360.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FraudGatewayTests {
 @Test void kafkaFailureFailsClosed() {
   JdbcTemplate db=mock(JdbcTemplate.class);
   @SuppressWarnings("unchecked")
   KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
   when(kafka.send(eq("payments.fraud.requests"),anyString(),anyString()))
       .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));
   var gateway=new FraudGateway(db,kafka,new ObjectMapper(),200);
   var ex=catchThrowableOfType(()->gateway.evaluate(UUID.randomUUID(),"a".repeat(64)),
                                ResponseStatusException.class);
   assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
   verifyNoInteractions(db);
 }
 @Test void mustWaitForDecisionAppliedByKafkaConsumer() {
   JdbcTemplate db=mock(JdbcTemplate.class);
   @SuppressWarnings("unchecked")
   KafkaTemplate<String,String> kafka=mock(KafkaTemplate.class);
   when(kafka.send(eq("payments.fraud.requests"),anyString(),anyString()))
       .thenReturn(CompletableFuture.completedFuture(null));
   UUID id=UUID.randomUUID();
   when(db.queryForObject(startsWith("SELECT status"),eq(String.class),eq(id))).thenReturn("BLOCKED");
   assertThatCode(()->new FraudGateway(db,kafka,new ObjectMapper(),1000).evaluate(id,"a".repeat(64)))
       .doesNotThrowAnyException();
   verify(db).queryForObject(startsWith("SELECT status"),eq(String.class),eq(id));
 }
}
