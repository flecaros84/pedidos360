package cl.duoc.pedidos360.products.config;

import cl.duoc.pedidos360.products.domain.Branch;
import cl.duoc.pedidos360.products.domain.ShippingRate;
import cl.duoc.pedidos360.products.repository.BranchRepository;
import cl.duoc.pedidos360.products.repository.ShippingRateRepository;
import cl.duoc.pedidos360.products.service.ShippingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** Tarifas/tiempos simulados e idempotentes; nunca modifica datos ya existentes. */
@Component @Order(3)
public class DemoShippingSeeder implements ApplicationRunner {
    private final BranchRepository branches;
    private final ShippingRateRepository rates;
    private final boolean enabled;
    public DemoShippingSeeder(BranchRepository branches, ShippingRateRepository rates,
               @Value("${app.catalog.seed-demo:false}") boolean enabled) {
        this.branches = branches; this.rates = rates; this.enabled = enabled;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!enabled) return;
        var zones = ShippingService.ZONES.keySet().stream().sorted().toList();
        for (Branch branch : branches.findAll()) {
            for (String zone : zones) {
                if (rates.existsByBranchCodeAndZoneCode(branch.getCode(), zone)) continue;
                // Tarifa por trayecto, no distancia geográfica real.
                boolean local = branch.getCode().equals("PM-CENTRO") && zone.equals("PM")
                    || branch.getCode().equals("PM-ALERCE") && zone.equals("ALERCE")
                    || branch.getCode().equals("PV-CENTRO") && zone.equals("PV")
                    || branch.getCode().equals("FRUTILLAR") && zone.equals("FR")
                    || branch.getCode().equals("CALBUCO") && zone.equals("CAL");
                int branchRank = switch (branch.getCode()) {
                    case "PM-CENTRO" -> 0; case "PM-ALERCE" -> 1; case "PV-CENTRO" -> 2;
                    case "FRUTILLAR" -> 3; default -> 4;
                };
                int zoneRank = switch (zone) {
                    case "PM" -> 0; case "ALERCE" -> 1; case "PV" -> 2;
                    case "FR" -> 3; default -> 4;
                };
                int gap = Math.abs(branchRank - zoneRank);
                int cost = local ? 1800 : 2500 + gap * 600;
                int days = local ? 1 : (gap <= 2 ? 2 : 3);
                rates.save(new ShippingRate(branch, zone, BigDecimal.valueOf(cost), days));
            }
        }
    }
}
