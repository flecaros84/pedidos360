package cl.duoc.pedidos360.cart.config;

import cl.duoc.pedidos360.cart.domain.CartItem;
import cl.duoc.pedidos360.cart.domain.CartItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migración idempotente para datos heredados del carrito original.
 * Mantiene un registro por (usuario, SKU), suma cantidades y elimina SOLO duplicados.
 * Se ejecuta antes de que Spring Boot termine de iniciar y exponga el servicio.
 * En producción, debe respaldarse la BD antes del primer despliegue de esta versión.
 */
@Component
public class CartDataMigration implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(CartDataMigration.class);
    private final CartItemRepository repository;
    private final JdbcTemplate jdbc;

    public CartDataMigration(CartItemRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    private record Key(String userId, String productCode) { }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // v0.4: branch_code deja de intervenir en las decisiones del carrito.
        // Se conserva la columna heredada para no perder datos ni forzar una migración destructiva.
        List<CartItem> items = repository.findAll();
        Map<Key, CartItem> keepers = new LinkedHashMap<>();
        List<CartItem> duplicates = new ArrayList<>();

        for (CartItem item : items) {
            Key key = new Key(item.getUserId(), item.getProductCode());
            CartItem keep = keepers.putIfAbsent(key, item);
            if (keep != null) {
                keep.incrementQuantity(item.getQuantity());
                duplicates.add(item);
            }
        }

        if (!duplicates.isEmpty()) {
            repository.deleteAll(duplicates);
            repository.flush();
            logger.info("Consolidados {} registros duplicados en cart_item", duplicates.size());
        }
        // Índice único conservado tras los reinicios; evita filas duplicadas nuevas.
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_cart_item_user_product "
                + "ON cart_item(user_id, product_code)");
    }
}
