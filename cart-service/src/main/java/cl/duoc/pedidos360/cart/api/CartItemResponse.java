package cl.duoc.pedidos360.cart.api;

import java.time.Instant;

import cl.duoc.pedidos360.cart.domain.CartItem;

public record CartItemResponse(
        Long id,
        String productCode,
        String productName,
        Integer quantity,
        Instant createdAt) {

    public static CartItemResponse from(CartItem item) {
        return new CartItemResponse(
                item.getId(),
                item.getProductCode(),
                item.getProductName(),
                item.getQuantity(),
                item.getCreatedAt());
    }
}
