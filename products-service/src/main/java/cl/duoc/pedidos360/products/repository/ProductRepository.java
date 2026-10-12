package cl.duoc.pedidos360.products.repository;

import cl.duoc.pedidos360.products.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    boolean existsBySku(String sku);

    Optional<Product> findBySku(String sku);

    Optional<Product> findByIdAndActiveTrue(Long id);

    @Query("select distinct p.category from Product p where p.active = true order by p.category")
    List<String> findActiveCategories();
}
