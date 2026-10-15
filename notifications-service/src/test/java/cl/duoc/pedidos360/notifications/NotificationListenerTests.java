package cl.duoc.pedidos360.notifications;
import cl.duoc.pedidos360.notifications.domain.SentNotification;
import cl.duoc.pedidos360.notifications.repository.SentNotificationRepository;
import cl.duoc.pedidos360.notifications.service.OrderNotificationListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
class NotificationListenerTests {
    @Test void procesaEventoYRegistraEnvio() throws Exception {
        var repo=mock(SentNotificationRepository.class);
        var mail=mock(JavaMailSender.class);
        UUID eventId=UUID.randomUUID(), orderId=UUID.randomUUID();
        String body="{\"eventId\":\""+eventId+"\",\"orderId\":\""+orderId+"\","
            +"\"recipientEmail\":\"demo@example.cl\",\"zoneCode\":\"PM\","
            +"\"mode\":\"CHEAPEST\",\"totalAmount\":250,"
            +"\"quote\":{\"shipments\":[{\"branchName\":\"Puerto Montt\","
            +"\"shippingCost\":50,\"items\":[{\"name\":\"Paracetamol\",\"quantity\":2}]}]}}";
        new OrderNotificationListener(repo,mail,new ObjectMapper(),"demo@farmaciasimple.local").created(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var email=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(email.capture());
        assertThat(email.getValue().getTo()).contains("demo@example.cl");
        assertThat(email.getValue().getText()).contains("Paracetamol");
        verify(repo).saveAndFlush(any(SentNotification.class));
    }
    @Test void eventoRepetidoNoReenviaCorreo() throws Exception {
        var repo=mock(SentNotificationRepository.class);
        var mail=mock(JavaMailSender.class);
        UUID eventId=UUID.randomUUID();
        when(repo.existsById(eventId)).thenReturn(true);
        new OrderNotificationListener(repo,mail,new ObjectMapper(),"demo@farmaciasimple.local")
            .created(("{\"eventId\":\""+eventId+"\",\"orderId\":\""+UUID.randomUUID()+"\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verifyNoInteractions(mail);
    }
}
