package cl.duoc.pedidos360.orders.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

/** Retries cart cleanup for PAID orders without holding a DB transaction during network calls. */
@Component
public class PaidCartReconciler {
    private static final Logger log = LoggerFactory.getLogger(PaidCartReconciler.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RestClient cart;
    private final String token;

    record PaidOrder(UUID id, String userId, String quoteJson) {}

    public PaidCartReconciler(JdbcTemplate jdbc, ObjectMapper json,
            @Value("${app.cart.base-url}") String cartUrl,
            @Value("${app.internal.service-token:}") String token) {
        this.jdbc = jdbc;
        this.json = json;
        this.cart = RestClient.builder().baseUrl(cartUrl).build();
        this.token = token;
    }

    @Scheduled(fixedDelayString = "${app.cart.cleanup-ms:5000}", initialDelay = 10000)
    public void reconcile() {
        if (token.isBlank()) return;
        try {
            List<PaidOrder> pending = jdbc.query(
                "SELECT id,user_id,quote_json FROM orders.customer_orders o "
                + "WHERE status='PAID' AND NOT EXISTS "
                + "(SELECT 1 FROM orders.cart_cleared c WHERE c.order_id=o.id) "
                + "ORDER BY created_at LIMIT 25",
                (rs, i) -> new PaidOrder(rs.getObject("id", UUID.class),
                        rs.getString("user_id"), rs.getString("quote_json")));
            for (PaidOrder order : pending) {
                try {
                    JsonNode shipments = json.readTree(order.quoteJson()).path("shipments");
                    if (!shipments.isArray() || shipments.isEmpty()) throw new IllegalStateException("Missing paid quote");
                    List<Map<String, Object>> lines = new ArrayList<>();
                    for (JsonNode shipment : shipments) {
                        for (JsonNode item : shipment.path("items")) {
                            String sku = item.path("sku").asText("");
                            int quantity = item.path("quantity").asInt(0);
                            if (sku.isBlank() || quantity < 1) throw new IllegalStateException("Invalid paid quote");
                            lines.add(Map.of("sku", sku, "quantity", quantity));
                        }
                    }
                    cart.post().uri("/internal/cart/paid")
                        .header("X-Service-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("orderId", order.id().toString(), "userId", order.userId(), "items", lines))
                        .retrieve().toBodilessEntity();
                    jdbc.update("INSERT INTO orders.cart_cleared(order_id) VALUES (?) ON CONFLICT DO NOTHING", order.id());
                } catch (Exception error) {
                    log.warn("Cart cleanup pending for order {}: {}", order.id(), error.toString());
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to reconcile paid carts: {}", ex.toString());
        }
    }
}
