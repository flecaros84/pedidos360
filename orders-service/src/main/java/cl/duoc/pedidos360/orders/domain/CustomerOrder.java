package cl.duoc.pedidos360.orders.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="customer_orders",schema="orders",uniqueConstraints={
    @UniqueConstraint(name="uk_order_request",columnNames={"user_id","request_key"})})
public class CustomerOrder {
    @Id private UUID id;
    @Column(name="user_id",nullable=false,length=120) private String userId;
    @Column(name="request_key",nullable=false,length=80) private String requestKey;
    @Column(name="recipient_email",nullable=false,length=254) private String recipientEmail;
    @Column(name="zone_code",nullable=false,length=12) private String zoneCode;
    @Column(nullable=false,length=10) private String mode;
    @Column(nullable=false,length=32) private String status;
    @Column(name="total_amount",nullable=false,precision=16,scale=2) private BigDecimal totalAmount;
    @Column(name="quote_json",nullable=false,columnDefinition="text") private String quoteJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected CustomerOrder() {}
    public CustomerOrder(String userId,String key,String email,String zone,String mode,BigDecimal total,String json) {
        this.id=UUID.randomUUID(); this.userId=userId; this.requestKey=key;
        this.recipientEmail=email; this.zoneCode=zone; this.mode=mode;
        this.status="PENDING_PAYMENT"; this.totalAmount=total;
        this.quoteJson=json; this.createdAt=Instant.now();
    }
    public UUID getId(){return id;} public String getUserId(){return userId;}
    public String getRecipientEmail(){return recipientEmail;} public String getZoneCode(){return zoneCode;}
    public String getMode(){return mode;} public String getStatus(){return status;}
    public BigDecimal getTotalAmount(){return totalAmount;} public String getQuoteJson(){return quoteJson;}
    public Instant getCreatedAt(){return createdAt;}
}
