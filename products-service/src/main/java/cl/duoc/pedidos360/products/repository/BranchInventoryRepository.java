package cl.duoc.pedidos360.products.repository;
import cl.duoc.pedidos360.products.domain.BranchInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface BranchInventoryRepository extends JpaRepository<BranchInventory, Long> {
    Optional<BranchInventory> findByBranchCodeAndProductSku(String branchCode, String sku);
    boolean existsByBranchCodeAndProductId(String branchCode, Long productId);
    @Query("select i from BranchInventory i join fetch i.branch join fetch i.product where i.product.sku in :skus and i.product.active = true and i.branch.active = true")
    List<BranchInventory> findForSkus(@Param("skus") List<String> skus);
    @Query("select i from BranchInventory i join fetch i.branch where i.product.id = :id and i.branch.active = true order by i.branch.name")
    List<BranchInventory> findForProduct(@Param("id") Long id);
    @Query("select i from BranchInventory i join fetch i.product where i.branch.code = :branch and i.branch.active = true and i.product.active = true")
    List<BranchInventory> findForBranch(@Param("branch") String branchCode);
}
