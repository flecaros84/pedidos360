package cl.duoc.pedidos360.orders.repository;
import cl.duoc.pedidos360.orders.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface OutboxRepository extends JpaRepository<OutboxEvent,UUID> {
    List<OutboxEvent> findTop25ByPublishedAtIsNullOrderByCreatedAtAsc();
}
