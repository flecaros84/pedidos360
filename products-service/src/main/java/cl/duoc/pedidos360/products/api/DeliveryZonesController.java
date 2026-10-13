package cl.duoc.pedidos360.products.api;

import cl.duoc.pedidos360.products.service.ShippingService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/delivery-zones")
public class DeliveryZonesController {
    private final ShippingService shipping;
    public DeliveryZonesController(ShippingService shipping) { this.shipping = shipping; }
    @GetMapping public List<DeliveryZoneResponse> zones() { return shipping.zones(); }
    @GetMapping("/{zoneCode}/rates")
    public List<ShippingRateResponse> rates(@PathVariable String zoneCode) { return shipping.rates(zoneCode); }
}
