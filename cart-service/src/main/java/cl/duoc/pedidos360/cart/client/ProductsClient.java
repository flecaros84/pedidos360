package cl.duoc.pedidos360.cart.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

/** Consulta la fuente de precios y stock. En AWS usa ALB interno; en local, red Docker. */
@Component
public class ProductsClient {
    private final RestClient rest;
    public ProductsClient(RestClient.Builder builder, @Value("${app.products.base-url:http://localhost:8083}") String url) {
        this.rest = builder.baseUrl(url).build();
    }
    public record Availability(String branchCode, String branchName, String sku, String name,
                               BigDecimal price, int stock) {}
    public record ShippingRate(String branchCode, String branchName, String zoneCode,
                               BigDecimal cost, int deliveryDays) {}

    public List<Availability> offers(List<String> skus, String bearer) {
        if (skus.isEmpty()) return List.of();
        try {
            Availability[] response = rest.get().uri(uri -> uri.path("/api/products/offers")
                   .queryParam("skus", String.join(",", skus)).build())
                .headers(h -> authorize(h, bearer)).retrieve().body(Availability[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
        catch (ResourceAccessException ex) { throw offline(); }
    }
    public List<ShippingRate> shipping(String zone, String bearer) {
        try {
            ShippingRate[] response = rest.get().uri("/api/delivery-zones/{zone}/rates", zone)
                .headers(h -> authorize(h, bearer)).retrieve().body(ShippingRate[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException ex) { throw upstream(ex); }
        catch (ResourceAccessException ex) { throw offline(); }
    }
    private static void authorize(HttpHeaders headers, String bearer) {
        if (bearer != null && bearer.startsWith("Bearer ")) headers.set(HttpHeaders.AUTHORIZATION, bearer);
    }
    private static ResponseStatusException upstream(RestClientResponseException ex) {
        if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 404)
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU o zona de entrega inválida");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Products Service rechazó la consulta");
    }
    private static ResponseStatusException offline() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar Products Service");
    }
}
