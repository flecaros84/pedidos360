package cl.duoc.pedidos360.products.api;

import cl.duoc.pedidos360.products.domain.Product;
import cl.duoc.pedidos360.products.domain.BranchInventory;
import java.math.BigDecimal;
import java.util.List;

/** Contrato compatible v0.3 + resumen multitienda v0.4. */
public record ProductResponse(Long id, String sku, String name, String activeIngredient,
        String strength, String presentation, String laboratory, String category,
        BigDecimal referencePrice, boolean requiresPrescription,
        String branchCode, BigDecimal price, Integer stock,
        BigDecimal minPrice, Integer totalStock) {
    public static ProductResponse from(Product p) { return from(p, null); }
    public static ProductResponse from(Product p, BranchInventory i) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getActiveIngredient(),
                p.getStrength(), p.getPresentation(), p.getLaboratory(), p.getCategory(),
                p.getReferencePrice(), p.isRequiresPrescription(),
                i == null ? null : i.getBranch().getCode(),
                i == null ? null : i.getPrice(), i == null ? null : i.getStock(),
                i != null && i.getStock() > 0 ? i.getPrice() : null,
                i == null ? null : i.getStock());
    }
    public static ProductResponse summary(Product p, List<BranchInventory> inventory) {
        var available = inventory.stream().filter(i -> i.getStock() > 0).toList();
        BigDecimal lowest = available.stream().map(BranchInventory::getPrice)
              .min(BigDecimal::compareTo).orElse(null);
        int stock = inventory.stream().mapToInt(i -> Math.max(0, i.getStock())).sum();
        ProductResponse base = from(p);
        return new ProductResponse(base.id(), base.sku(), base.name(), base.activeIngredient(),
            base.strength(), base.presentation(), base.laboratory(), base.category(),
            base.referencePrice(), base.requiresPrescription(), null, null, null, lowest, stock);
    }
}
