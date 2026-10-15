package cl.duoc.pedidos360.orders.client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
@Component
public class CartClient {
    private final RestClient client;
    public CartClient(RestClient.Builder builder, @Value("${app.cart.base-url}") String base) {
        this.client=builder.baseUrl(base).build();
    }
    public record Line(String sku,String name,int quantity,BigDecimal unitPrice,BigDecimal subtotal) {}
    public record Shipment(String branchCode,String branchName,BigDecimal shippingCost,int deliveryDays,List<Line> items) {}
    public record Option(String mode,BigDecimal medicinesTotal,BigDecimal shippingTotal,BigDecimal total,int estimatedDays,List<Shipment> shipments) {}
    public record Quote(String zoneCode,Option cheapest,Option fastest,String disclaimer) {}
    public Quote quote(String zoneCode,String bearer,String demoUser) {
        try {
            Quote result=client.get().uri(uri->uri.path("/api/cart/quote").queryParam("zoneCode",zoneCode).build())
                .headers(h -> {
                    if (bearer!=null && bearer.startsWith("Bearer ")) h.set(HttpHeaders.AUTHORIZATION,bearer);
                    if (demoUser!=null) h.set("X-Demo-User",demoUser);
                }).retrieve().body(Quote.class);
            if (result==null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Cart devolvió vacío");
            return result;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"No se pudo verificar la cotización con Cart");
        }
    }
}
