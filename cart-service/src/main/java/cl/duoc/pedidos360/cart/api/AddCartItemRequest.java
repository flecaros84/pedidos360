package cl.duoc.pedidos360.cart.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotBlank String productCode,
        @NotBlank String productName,
        @NotNull @Min(1) Integer quantity) {
}
