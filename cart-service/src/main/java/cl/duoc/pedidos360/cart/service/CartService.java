package cl.duoc.pedidos360.cart.service;

import cl.duoc.pedidos360.cart.api.AddCartItemRequest;
import cl.duoc.pedidos360.cart.api.CartItemResponse;
import cl.duoc.pedidos360.cart.domain.CartItem;
import cl.duoc.pedidos360.cart.domain.CartItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CartService {
    private final CartItemRepository repository;
    private final TransactionTemplate transactions;

    public CartService(CartItemRepository repository, TransactionTemplate transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    public List<CartItemResponse> list(String userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(CartItemResponse::from).toList();
    }

    public CartItemResponse add(String userId, AddCartItemRequest body) {
        // UNIQUE(user_id, product_code) cubre también inserciones concurrentes.
        // Si dos peticiones insertan por primera vez el mismo SKU, reintentamos en otra
        // transacción para incrementar la fila creada por la otra petición.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transactions.execute(status -> {
                    CartItem item = repository.findByUserIdAndProductCode(userId, body.productCode())
                            .orElseGet(() -> new CartItem(
                                    userId, body.productCode(), body.productName(), body.quantity()));
                    if (item.getId() != null) {
                        try {
                            item.incrementQuantity(body.quantity());
                        } catch (ArithmeticException ex) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cantidad excede el límite permitido");
                        }
                    }
                    // saveAndFlush fuerza la comprobación de la restricción antes
                    // de terminar la transacción (y permite detectar una colisión).
                    return CartItemResponse.from(repository.saveAndFlush(item));
                });
            } catch (DataIntegrityViolationException ex) {
                if (attempt == 2) throw ex;
            }
        }
        throw new IllegalStateException("No fue posible agregar el producto");
    }

    public void delete(String userId, Long id) {
        transactions.executeWithoutResult(status -> {
            CartItem item = repository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
            repository.delete(item);
        });
    }
}
