package cl.duoc.pedidos360.orders.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaAuditOutboxPublisher {
    private final JdbcTemplate db;
    private final KafkaTemplate<String, String> kafka;

    public KafkaAuditOutboxPublisher(JdbcTemplate db, KafkaTemplate<String, String> kafka) {
        this.db = db;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 20000)
    public void publish() {
        try {
            var batch = db.queryForList("SELECT id,routing_key,payload_json FROM orders.event_outbox " +
                    "WHERE audit_published_at IS NULL ORDER BY created_at LIMIT 25");
            for (var row : batch) {
                kafka.send("audit.events", (String) row.get("routing_key"),
                        (String) row.get("payload_json")).get(5, TimeUnit.SECONDS);
                db.update("UPDATE orders.event_outbox SET audit_published_at=NOW() WHERE id=?",
                        (UUID) row.get("id"));
            }
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(getClass())
                    .warn("Kafka audit delivery will retry: {}", ex.toString());
        }
    }
}
