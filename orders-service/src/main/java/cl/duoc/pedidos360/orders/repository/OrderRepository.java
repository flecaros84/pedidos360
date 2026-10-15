package cl.duoc.pedidos360.orders.repository;
import cl.duoc.pedidos360.orders.domain.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface OrderRepository extends JpaRepository<CustomerOrder,UUID> {
    List<CustomerOrder> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<CustomerOrder> findByIdAndUserId(UUID id,String userId);
    Optional<CustomerOrder> findByUserIdAndRequestKey(String userId,String requestKey);
}
