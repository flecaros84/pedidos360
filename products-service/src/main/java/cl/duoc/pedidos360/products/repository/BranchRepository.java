package cl.duoc.pedidos360.products.repository;
import cl.duoc.pedidos360.products.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BranchRepository extends JpaRepository<Branch, String> {
    List<Branch> findByActiveTrueOrderByNameAsc();
}
