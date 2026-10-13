package cl.duoc.pedidos360.products.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "branches", schema = "catalogo")
public class Branch {
    @Id @Column(length = 32) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 80) private String city;
    @Column(nullable = false) private boolean active = true;
    protected Branch() {}
    public Branch(String code, String name, String city) {
        this.code = code; this.name = name; this.city = city;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public boolean isActive() { return active; }
}
