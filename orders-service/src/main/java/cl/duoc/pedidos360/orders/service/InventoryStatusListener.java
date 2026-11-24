package cl.duoc.pedidos360.orders.service;
import cl.duoc.pedidos360.orders.config.RabbitConfig;
import cl.duoc.pedidos360.orders.domain.OutboxEvent;
import cl.duoc.pedidos360.orders.repository.OrderRepository;
import cl.duoc.pedidos360.orders.repository.OutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
@Component
public class InventoryStatusListener {
 private final OrderRepository orders;private final OutboxRepository outbox;private final ObjectMapper mapper;
 public InventoryStatusListener(OrderRepository orders,OutboxRepository outbox,ObjectMapper mapper){
    this.orders=orders;this.outbox=outbox;this.mapper=mapper;
 }
 @Transactional
 @RabbitListener(queues="orders.inventory-final")
 public void consume(byte[] body) throws Exception {
   JsonNode event=mapper.readTree(body);
   UUID orderId=UUID.fromString(event.path("orderId").asText());
   var order=orders.findById(orderId).orElseThrow();
   if(!"PENDING_PAYMENT".equals(order.getStatus())) return;
   String type=event.path("type").asText();
   if("inventory.committed".equals(type)) {
      order.markPaid();orders.save(order);
      Map<String,Object> notice=new LinkedHashMap<>();
      notice.put("eventId",UUID.randomUUID().toString());
      notice.put("type","order.paid");notice.put("orderId",order.getId().toString());
      notice.put("recipientEmail",order.getRecipientEmail());
      notice.put("totalAmount",order.getTotalAmount());
      notice.put("zoneCode",order.getZoneCode());notice.put("mode",order.getMode());
      notice.put("quote",mapper.readTree(order.getQuoteJson()));
      outbox.save(new OutboxEvent(RabbitConfig.KEY,mapper.writeValueAsString(notice)));
   } else if("inventory.released".equals(type)) {
      order.markPaymentFailed();orders.save(order);
   }
 }
}
