package cl.duoc.pedidos360.notifications.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="sent_notifications",schema="notifications")
public class SentNotification {
    @Id @Column(name="event_id") private UUID eventId;
    @Column(name="order_id",nullable=false) private UUID orderId;
    @Column(name="sent_at",nullable=false) private Instant sentAt;
    protected SentNotification() {}
    public SentNotification(UUID eventId,UUID orderId) {
        this.eventId=eventId;this.orderId=orderId;this.sentAt=Instant.now();
    }
}
