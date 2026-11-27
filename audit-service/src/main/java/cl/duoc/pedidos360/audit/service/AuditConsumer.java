package cl.duoc.pedidos360.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class AuditConsumer {
    private final JdbcTemplate db;
    private final ObjectMapper json;

    public AuditConsumer(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    @KafkaListener(topics = "audit.events", groupId = "audit")
    public void consume(String body) throws Exception {
        JsonNode event = json.readTree(body);
        UUID eventId = UUID.fromString(event.path("eventId").asText());
        String type = event.path("type").asText("unknown");
        // Preserve event identity for idempotent Kafka re-delivery; do not store card secrets.
        db.update("INSERT INTO audit.audit_events(event_id,event_type,source,body) " +
                  "VALUES (?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
                  eventId, type, "kafka", body);
    }
}
