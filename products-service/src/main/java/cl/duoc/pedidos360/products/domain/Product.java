package cl.duoc.pedidos360.products.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products", schema = "catalogo")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 24)
    private String sku;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "active_ingredient", length = 160)
    private String activeIngredient;

    @Column(length = 60)
    private String strength;

    @Column(nullable = false, length = 100)
    private String presentation;

    @Column(length = 120)
    private String laboratory;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(name = "reference_price", precision = 12, scale = 0)
    private BigDecimal referencePrice;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription;

    @Column(nullable = false)
    private boolean active = true;

    protected Product() {
        // Requerido por JPA.
    }

    public Product(String sku, String name, String activeIngredient, String strength,
                   String presentation, String laboratory, String category,
                   BigDecimal referencePrice, boolean requiresPrescription) {
        this.sku = sku;
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.strength = strength;
        this.presentation = presentation;
        this.laboratory = laboratory;
        this.category = category;
        this.referencePrice = referencePrice;
        this.requiresPrescription = requiresPrescription;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getActiveIngredient() { return activeIngredient; }
    public String getStrength() { return strength; }
    public String getPresentation() { return presentation; }
    public String getLaboratory() { return laboratory; }
    public String getCategory() { return category; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public boolean isRequiresPrescription() { return requiresPrescription; }
    public boolean isActive() { return active; }
}
