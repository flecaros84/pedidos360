package cl.duoc.pedidos360.notifications.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationTestController {
    private final JavaMailSender mail;
    private final String from;
    private final boolean enabled;

    public NotificationTestController(JavaMailSender mail,
         @Value("${app.mail.from}") String from,
         @Value("${app.mail.allow-test:false}") boolean enabled) {
        this.mail = mail; this.from = from; this.enabled = enabled;
    }
    public record TestEmail(@NotBlank @Email @Size(max=254) String recipient,
                            @Size(max=160) String message) {}

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> test(@Valid @RequestBody TestEmail data) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint deshabilitado");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(data.recipient());
        message.setSubject("Farmacia Simple - Prueba de notificaciones");
        message.setText(data.message() == null || data.message().isBlank() ?
                "Correo de prueba desde Postman." : data.message());
        mail.send(message);
        return Map.of("eventId", UUID.randomUUID().toString(), "sentAt", Instant.now().toString(),
                "result", "SENT");
    }
}
