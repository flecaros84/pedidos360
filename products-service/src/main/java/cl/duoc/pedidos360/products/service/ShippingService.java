package cl.duoc.pedidos360.products.service;

import cl.duoc.pedidos360.products.api.*;
import cl.duoc.pedidos360.products.repository.ShippingRateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

/** Zonas y tarifas ficticias configuradas en la BD, no tiempos prometidos de entrega. */
@Service @Transactional(readOnly = true)
public class ShippingService {
    public static final Map<String,String> ZONES = Map.of(
       "PM", "Puerto Montt", "ALERCE", "Alerce", "PV", "Puerto Varas",
       "FR", "Frutillar", "CAL", "Calbuco");
    private final ShippingRateRepository repository;
    public ShippingService(ShippingRateRepository repository) { this.repository = repository; }
    public List<DeliveryZoneResponse> zones() {
        return List.of(new DeliveryZoneResponse("PM", "Puerto Montt"),
            new DeliveryZoneResponse("ALERCE", "Alerce"),
            new DeliveryZoneResponse("PV", "Puerto Varas"),
            new DeliveryZoneResponse("FR", "Frutillar"),
            new DeliveryZoneResponse("CAL", "Calbuco"));
    }
    public List<ShippingRateResponse> rates(String zoneCode) {
        if (zoneCode == null || !ZONES.containsKey(zoneCode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zona de entrega inválida");
        return repository.findByZoneCodeAndBranchActiveTrueOrderByBranchCodeAsc(zoneCode)
            .stream().map(ShippingRateResponse::from).toList();
    }
}
