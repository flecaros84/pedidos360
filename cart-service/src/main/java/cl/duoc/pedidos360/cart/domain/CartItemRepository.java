package cl.duoc.pedidos360.cart.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CartItem> findByIdAndUserId(Long id, String userId);
}
