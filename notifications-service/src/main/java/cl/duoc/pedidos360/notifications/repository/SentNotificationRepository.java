package cl.duoc.pedidos360.notifications.repository;
import cl.duoc.pedidos360.notifications.domain.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SentNotificationRepository extends JpaRepository<SentNotification,UUID> {}
