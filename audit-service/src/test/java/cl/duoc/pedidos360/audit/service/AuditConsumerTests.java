package cl.duoc.pedidos360.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuditConsumerTests {
 @Test void storesOnlyKafkaSourceAndDeduplicatesByEventId() throws Exception {
   JdbcTemplate db=mock(JdbcTemplate.class);
   ObjectMapper json=new ObjectMapper();
   UUID id=UUID.randomUUID();
   String payload=json.writeValueAsString(Map.of("eventId",id.toString(),
                "type","order.created","orderId",UUID.randomUUID().toString()));
   new AuditConsumer(db,json).consume(payload);
   verify(db).update(contains("ON CONFLICT (event_id) DO NOTHING"),
           eq(id),eq("order.created"),eq("kafka"),eq(payload));
 }
}
