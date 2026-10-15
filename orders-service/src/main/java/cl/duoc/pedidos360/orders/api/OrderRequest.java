package cl.duoc.pedidos360.orders.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
public record OrderRequest(
    @NotBlank @Pattern(regexp="[A-Z]{2,12}") String zoneCode,
    @NotBlank @Pattern(regexp="CHEAPEST|FASTEST") String mode,
    @NotBlank @Email @Size(max=254) String recipientEmail
) {}
