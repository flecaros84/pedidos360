package cl.duoc.pedidos360.cart.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomically subtract purchased quantities; retries never debit the cart twice. */
@Service
public class PaidOrderCleanup {
    public record Line(String sku, int quantity) {}
    public record Request(UUID orderId, String userId, List<Line> items) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PaidOrderCleanup(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public void apply(Request request) {
        if (request == null || request.orderId() == null || request.userId() == null
                || request.userId().isBlank() || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paid-order cleanup");
        }
        Map<String, Integer> quantities = new LinkedHashMap<>();
        try {
            for (Line line : request.items()) {
                if (line == null || line.sku() == null || !line.sku().matches("[A-Za-z0-9_-]{1,50}")
                        || line.quantity() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paid-order line");
                }
                quantities.merge(line.sku(), line.quantity(), Math::addExact);
            }
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paid-order quantity overflow");
        }

        transactions.executeWithoutResult(status -> {
            int first = jdbc.update(
                    "INSERT INTO cart_paid_orders(order_id) VALUES (?) ON CONFLICT DO NOTHING",
                    request.orderId());
            if (first == 0) return;
            for (var entry : quantities.entrySet()) {
                String sku = entry.getKey();
                int quantity = entry.getValue();
                // Keep additional quantities that were not part of the paid quote.
                jdbc.update("DELETE FROM cart_item WHERE user_id=? AND product_code=? AND quantity<=?",
                        request.userId(), sku, quantity);
                jdbc.update("UPDATE cart_item SET quantity=quantity-? WHERE user_id=? AND product_code=? AND quantity>?",
                        quantity, request.userId(), sku, quantity);
            }
        });
    }
}
