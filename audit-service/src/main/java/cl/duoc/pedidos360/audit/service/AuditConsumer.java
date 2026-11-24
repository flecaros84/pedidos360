package cl.duoc.pedidos360.audit.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class AuditConsumer {
    private final JdbcTemplate db; private final ObjectMapper json;
    public AuditConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    @RabbitListener(queues="audit.all-events")
    public void receive(org.springframework.amqp.core.Message message) throws Exception {
        // Use the raw AMQP body to avoid content-type dependent conversion.
        consume(message.getBody());
    }
    public void consume(byte[] body) throws Exception {
        JsonNode event=json.readTree(body);
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        String type=event.path("type").asText("unknown");
        // Store only the business event; payment card numbers and secrets must never appear.
        db.update("INSERT INTO audit.audit_events(event_id,event_type,source,body) VALUES (?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
             eventId,type,"rabbitmq",new String(body,java.nio.charset.StandardCharsets.UTF_8));
    }
}
