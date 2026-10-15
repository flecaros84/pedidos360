package cl.duoc.pedidos360.orders.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="event_outbox",schema="orders")
public class OutboxEvent {
    @Id private UUID id;
    @Column(name="routing_key",nullable=false) private String routingKey;
    @Column(name="payload_json",nullable=false,columnDefinition="text") private String payloadJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="published_at") private Instant publishedAt;
    protected OutboxEvent() {}
    public OutboxEvent(String routingKey,String payloadJson) {
        this.id=UUID.randomUUID(); this.routingKey=routingKey;
        this.payloadJson=payloadJson; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public String getRoutingKey(){return routingKey;}
    public String getPayloadJson(){return payloadJson;}
    public void markPublished(){this.publishedAt=Instant.now();}
}
