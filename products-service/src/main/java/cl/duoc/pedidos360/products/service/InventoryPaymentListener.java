package cl.duoc.pedidos360.products.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class InventoryPaymentListener {
  private final InventoryService inventory; private final ObjectMapper mapper;
  public InventoryPaymentListener(InventoryService inventory,ObjectMapper mapper){this.inventory=inventory;this.mapper=mapper;}
  @RabbitListener(queues="inventory.payment-results")
  public void receive(org.springframework.amqp.core.Message message) throws Exception {
      // Use the raw AMQP body to avoid content-type dependent conversion.
      consume(message.getBody());
  }
  public void consume(byte[] body) throws Exception {
    JsonNode event=mapper.readTree(body);
    String type=event.path("type").asText();
    if(!type.equals("payment.approved") && !type.equals("payment.rejected")) return;
    UUID id=UUID.fromString(event.path("orderId").asText());
    inventory.settle(id,type.equals("payment.approved")?"COMMITTED":"RELEASED");
  }
}
