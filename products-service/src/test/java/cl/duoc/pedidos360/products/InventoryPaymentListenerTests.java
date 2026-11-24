package cl.duoc.pedidos360.products;

import cl.duoc.pedidos360.products.service.InventoryPaymentListener;
import cl.duoc.pedidos360.products.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InventoryPaymentListenerTests {
    @Test
    void processesApprovedPaymentFromRawRabbitMessage() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        InventoryPaymentListener listener = new InventoryPaymentListener(inventory, new ObjectMapper());
        UUID orderId = UUID.randomUUID();
        String payload = "{\"type\":\"payment.approved\",\"orderId\":\"" + orderId + "\"}";
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        listener.receive(new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
        verify(inventory).settle(orderId, "COMMITTED");
    }

    @Test
    void processesRejectedPaymentFromRawRabbitMessage() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        InventoryPaymentListener listener = new InventoryPaymentListener(inventory, new ObjectMapper());
        UUID orderId = UUID.randomUUID();
        String payload = "{\"type\":\"payment.rejected\",\"orderId\":\"" + orderId + "\"}";
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN);
        listener.receive(new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
        verify(inventory).settle(orderId, "RELEASED");
    }
}
