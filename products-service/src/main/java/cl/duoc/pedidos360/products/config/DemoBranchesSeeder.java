package cl.duoc.pedidos360.products.config;

import cl.duoc.pedidos360.products.domain.*;
import cl.duoc.pedidos360.products.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

/** Solo datos de demostración; idempotente: jamás sobreescribe precios ni stock existente. */
@Component @Order(2)
public class DemoBranchesSeeder implements ApplicationRunner {
    private final ProductRepository products;
    private final BranchRepository branches;
    private final BranchInventoryRepository inventory;
    private final boolean enabled;
    public DemoBranchesSeeder(ProductRepository products, BranchRepository branches,
            BranchInventoryRepository inventory, @Value("${app.catalog.seed-demo:false}") boolean enabled) {
        this.products = products; this.branches = branches; this.inventory = inventory; this.enabled = enabled;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<Branch> demo = List.of(
            new Branch("PM-CENTRO", "Puerto Montt Centro", "Puerto Montt"),
            new Branch("PM-ALERCE", "Puerto Montt Alerce", "Puerto Montt"),
            new Branch("PV-CENTRO", "Puerto Varas Centro", "Puerto Varas"),
            new Branch("FRUTILLAR", "Frutillar", "Frutillar"),
            new Branch("CALBUCO", "Calbuco", "Calbuco")
        );
        for (Branch b : demo) if (!branches.existsById(b.getCode())) branches.save(b);
        var catalog = products.findAll();
        for (int branchIndex = 0; branchIndex < demo.size(); branchIndex++) {
            Branch b = branches.findById(demo.get(branchIndex).getCode()).orElseThrow();
            for (int productIndex = 0; productIndex < catalog.size(); productIndex++) {
                Product p = catalog.get(productIndex);
                if (inventory.existsByBranchCodeAndProductId(b.getCode(), p.getId())) continue;
                int base = p.getReferencePrice() == null ? 1990 : p.getReferencePrice().intValue();
                int price = base + branchIndex * 150;
                int stock = (branchIndex + productIndex) % 7 == 0 ? 0 : 4 + (branchIndex * 3 + productIndex * 2) % 19;
                inventory.save(new BranchInventory(b, p, BigDecimal.valueOf(price), stock));
            }
        }
    }
}
