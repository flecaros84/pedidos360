package cl.duoc.pedidos360.products.api;
import cl.duoc.pedidos360.products.domain.BranchInventory;
import java.math.BigDecimal;
public record AvailabilityResponse(String branchCode, String branchName,
        String sku, String name, BigDecimal price, Integer stock) {
    public static AvailabilityResponse from(BranchInventory i) {
        return new AvailabilityResponse(i.getBranch().getCode(), i.getBranch().getName(),
                i.getProduct().getSku(), i.getProduct().getName(), i.getPrice(), i.getStock());
    }
}
