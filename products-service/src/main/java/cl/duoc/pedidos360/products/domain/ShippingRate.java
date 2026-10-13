package cl.duoc.pedidos360.products.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "shipping_rates", schema = "catalogo", uniqueConstraints =
    @UniqueConstraint(name = "uq_shipping_branch_zone", columnNames = {"branch_code", "zone_code"}))
public class ShippingRate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_code", nullable = false) private Branch branch;
    @Column(name = "zone_code", nullable = false, length = 32) private String zoneCode;
    @Column(nullable = false, precision = 12, scale = 0) private BigDecimal cost;
    @Column(name = "delivery_days", nullable = false) private int deliveryDays;
    protected ShippingRate() {}
    public ShippingRate(Branch branch, String zoneCode, BigDecimal cost, int deliveryDays) {
        if (cost == null || cost.signum() < 0 || deliveryDays < 1) throw new IllegalArgumentException("Tarifa inválida");
        this.branch = branch; this.zoneCode = zoneCode; this.cost = cost; this.deliveryDays = deliveryDays;
    }
    public Branch getBranch() { return branch; }
    public String getZoneCode() { return zoneCode; }
    public BigDecimal getCost() { return cost; }
    public int getDeliveryDays() { return deliveryDays; }
}
