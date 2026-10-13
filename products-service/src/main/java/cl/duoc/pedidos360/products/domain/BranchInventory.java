package cl.duoc.pedidos360.products.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "branch_inventory", schema = "catalogo", uniqueConstraints =
    @UniqueConstraint(name = "uq_inventory_branch_product", columnNames = {"branch_code", "product_id"}))
public class BranchInventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", nullable = false) private Branch branch;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false) private Product product;
    @Column(nullable = false, precision = 12, scale = 0) private BigDecimal price;
    @Column(nullable = false) private Integer stock;
    protected BranchInventory() {}
    public BranchInventory(Branch branch, Product product, BigDecimal price, int stock) {
        if (price.signum() < 0 || stock < 0) throw new IllegalArgumentException("Precio y stock no pueden ser negativos");
        this.branch = branch; this.product = product; this.price = price; this.stock = stock;
    }
    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public Product getProduct() { return product; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
}
