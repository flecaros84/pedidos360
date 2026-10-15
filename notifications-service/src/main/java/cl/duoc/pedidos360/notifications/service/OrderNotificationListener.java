package cl.duoc.pedidos360.notifications.service;
import cl.duoc.pedidos360.notifications.domain.SentNotification;
import cl.duoc.pedidos360.notifications.repository.SentNotificationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.UUID;
@Component
public class OrderNotificationListener {
    private final SentNotificationRepository sent;
    private final JavaMailSender mail;
    private final ObjectMapper json;
    private final String from;
    public OrderNotificationListener(SentNotificationRepository sent,JavaMailSender mail,ObjectMapper json,
       @Value("${app.mail.from}") String from) {
        this.sent=sent;this.mail=mail;this.json=json;this.from=from;
    }
    @RabbitListener(queues="notifications.order-created")
    public void created(byte[] body) throws Exception {
        JsonNode event=json.readTree(body);
        UUID eventId=UUID.fromString(event.path("eventId").asText());
        UUID orderId=UUID.fromString(event.path("orderId").asText());
        if(sent.existsById(eventId)) return;
        String recipient=event.path("recipientEmail").asText();
        if(!recipient.matches("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$"))
            throw new IllegalArgumentException("Destinatario inválido");
        BigDecimal total=new BigDecimal(event.path("totalAmount").asText());
        StringBuilder lines=new StringBuilder();
        for(JsonNode shipment:event.path("quote").path("shipments")) {
            lines.append("\nSucursal: ").append(shipment.path("branchName").asText())
                .append(" | despacho: $").append(shipment.path("shippingCost").asText()).append(" CLP\n");
            for(JsonNode line:shipment.path("items"))
                lines.append("  • ").append(line.path("name").asText()).append(" x")
                    .append(line.path("quantity").asInt()).append("\n");
        }
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from);message.setTo(recipient);
        message.setSubject("Farmacia Simple - Pedido registrado " + orderId);
        message.setText("Hemos registrado tu pedido de demostración.\n"
            +"Pedido: "+orderId+"\nZona: "+event.path("zoneCode").asText()
            +"\nModalidad: "+event.path("mode").asText()
            +"\nTotal (con despacho): $"+total.toPlainString()+" CLP\n"
            +lines+"\nEstado: PENDIENTE DE PAGO. No existe pago real ni reserva de stock.\n");
        mail.send(message);
        // Se graba sólo después del envío; si falla SMTP, la cola reintenta y después manda a DLQ.
        // Crash después de SMTP y antes de este insert puede generar correos duplicados.
        sent.saveAndFlush(new SentNotification(eventId,orderId));
    }
}
