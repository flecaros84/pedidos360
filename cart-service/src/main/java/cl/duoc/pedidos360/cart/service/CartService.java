package cl.duoc.pedidos360.cart.service;

import cl.duoc.pedidos360.cart.api.*;
import cl.duoc.pedidos360.cart.client.ProductsClient;
import cl.duoc.pedidos360.cart.domain.*;
import cl.duoc.pedidos360.cart.quote.QuoteOptimizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** El carrito no elige sucursal: cotización asigna orígenes según costo total o plazo. */
@Service
public class CartService {
    private final CartItemRepository repository;
    private final TransactionTemplate transactions;
    private final ProductsClient products;
    public CartService(CartItemRepository repository, TransactionTemplate transactions, ProductsClient products) {
        this.repository = repository; this.transactions = transactions; this.products = products;
    }
    public List<CartItemResponse> list(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(CartItemResponse::from).toList();
    }
    public CartItemResponse add(String userId, AddCartItemRequest body, String authorization) {
        if (body.productCode() == null || !body.productCode().matches("[A-Za-z0-9_-]{1,50}")
            || body.quantity() == null || body.quantity() < 1 || body.quantity() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SKU o cantidad inválida");
        var offers = products.offers(List.of(body.productCode()), authorization);
        var skuOffers = offers.stream().filter(o -> body.productCode().equals(o.sku())).toList();
        if (skuOffers.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Producto no disponible");
        long stock = skuOffers.stream().mapToLong(o -> Math.max(0, o.stock())).sum();
        String officialName = skuOffers.get(0).name();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(status -> {
                    CartItem item = repository.findByUserIdAndProductCode(userId, body.productCode())
                        .orElseGet(() -> new CartItem(userId, body.productCode(), officialName, body.quantity(), null));
                    int total;
                    try { total = item.getId() == null ? body.quantity()
                            : Math.addExact(item.getQuantity(), body.quantity()); }
                    catch (ArithmeticException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad excesiva");
                    }
                    if (total > stock) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente en las cinco sucursales");
                    if (item.getId() != null) item.incrementQuantity(body.quantity());
                    return CartItemResponse.from(repository.saveAndFlush(item));
                });
            } catch (DataIntegrityViolationException ex) {
                if (attempt == 2) throw ex;
            }
        }
        throw new IllegalStateException("No fue posible agregar el producto");
    }
    public QuoteOptimizer.Result quote(String userId, String zone, String authorization) {
        if (zone == null || !zone.matches("[A-Z]{2,12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zona inválida");
        var items = repository.findByUserIdOrderByCreatedAtDesc(userId);
        if (items.isEmpty()) return new QuoteOptimizer.Result(zone, null, null,
            "Agrega productos para calcular alternativas de envío.");
        var lines = items.stream().map(i -> new QuoteOptimizer.RequestLine(
            i.getProductCode(), i.getProductName(), i.getQuantity())).toList();
        try {
            return QuoteOptimizer.quote(zone, lines,
                products.offers(items.stream().map(CartItem::getProductCode).distinct().toList(), authorization),
                products.shipping(zone, authorization));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }
    public void delete(String userId, Long id) {
        transactions.executeWithoutResult(status -> {
            CartItem item = repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ítem no encontrado"));
            repository.delete(item);
        });
    }
    public void clear(String userId) {
        transactions.executeWithoutResult(status -> repository.deleteByUserId(userId));
    }
}
