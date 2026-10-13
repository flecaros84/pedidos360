package cl.duoc.pedidos360.cart.api;
import cl.duoc.pedidos360.cart.domain.CartItem;
import java.time.Instant;
public record CartItemResponse(Long id, String productCode, String productName,
        Integer quantity, Instant createdAt, String branchCode) {
    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getId(), item.getProductCode(), item.getProductName(),
                item.getQuantity(), item.getCreatedAt(), item.getBranchCode());
    }
}
