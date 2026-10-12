package cl.duoc.pedidos360.cart.domain;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CartItem> findByIdAndUserId(Long id, String userId);

    // Bloqueo de la fila existente para evitar actualizaciones perdidas con POST simultáneos.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CartItem> findByUserIdAndProductCode(String userId, String productCode);
}
