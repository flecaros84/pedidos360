package cl.duoc.pedidos360.cart.domain;
import java.time.Instant;
import jakarta.persistence.*;
@Entity @Table(name = "cart_item")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, length = 100) private String userId;
    @Column(name = "product_code", nullable = false, length = 50) private String productCode;
    @Column(name = "product_name", nullable = false, length = 200) private String productName;
    @Column(name = "branch_code", length = 32) private String branchCode;
    @Column(nullable = false) private Integer quantity;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected CartItem() {}
    public CartItem(String userId, String code, String name, int quantity, String branchCode) {
        this.userId = userId; this.productCode = code; this.productName = name;
        this.quantity = quantity; this.branchCode = branchCode;
    }
    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
    public void incrementQuantity(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Cantidad inválida");
        quantity = Math.addExact(quantity, amount);
    }
    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getBranchCode() { return branchCode; }
    public Integer getQuantity() { return quantity; }
    public Instant getCreatedAt() { return createdAt; }
}
