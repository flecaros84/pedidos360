package cl.duoc.pedidos360.products.repository;

import cl.duoc.pedidos360.products.domain.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShippingRateRepository extends JpaRepository<ShippingRate, Long> {
    List<ShippingRate> findByZoneCodeAndBranchActiveTrueOrderByBranchCodeAsc(String zoneCode);
    boolean existsByBranchCodeAndZoneCode(String branchCode, String zoneCode);
}
