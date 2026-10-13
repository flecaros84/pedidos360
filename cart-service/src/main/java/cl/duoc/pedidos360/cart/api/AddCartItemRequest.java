package cl.duoc.pedidos360.cart.api;
import jakarta.validation.constraints.*;
/** El origen se decide en cotización: branchCode es opcional/ignorado por compatibilidad. */
public record AddCartItemRequest(@NotBlank String productCode, String productName,
        @NotNull @Min(1) Integer quantity, String branchCode) {}
