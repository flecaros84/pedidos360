package cl.duoc.pedidos360.cart.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CartItem> findByIdAndUserId(Long id, String userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CartItem> findByUserIdAndProductCode(String userId, String code);
    void deleteByUserId(String userId);
}
