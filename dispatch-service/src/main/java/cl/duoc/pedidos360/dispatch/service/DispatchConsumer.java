package cl.duoc.pedidos360.dispatch.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class DispatchConsumer {
    private final JdbcTemplate db; private final ObjectMapper json;
    public DispatchConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    @RabbitListener(queues="dispatch.order-paid")
    public void consume(byte[] body) throws Exception {
        JsonNode data=json.readTree(body);
        UUID id=UUID.fromString(data.path("orderId").asText());
        UUID eventId=UUID.fromString(data.path("eventId").asText());
        db.update("INSERT INTO dispatch.shipments(order_id,status,event_id) VALUES (?,'PENDING',?) ON CONFLICT (order_id) DO NOTHING",id,eventId);
    }
}
