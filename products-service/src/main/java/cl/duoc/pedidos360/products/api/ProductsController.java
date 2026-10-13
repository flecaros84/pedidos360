package cl.duoc.pedidos360.products.api;
import cl.duoc.pedidos360.products.service.ProductsService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/products")
public class ProductsController {
    private final ProductsService service;
    public ProductsController(ProductsService service) { this.service = service; }
    @GetMapping public List<ProductResponse> list(@RequestParam(required = false) String search,
            @RequestParam(required = false) String category, @RequestParam(required = false) String branchCode) {
        return service.list(search, category, branchCode);
    }
    // Bulk para la cotización: evita una solicitud HTTP por cada SKU/sucursal.
    @GetMapping("/offers") public List<AvailabilityResponse> offers(@RequestParam List<String> skus) {
        return service.offers(skus);
    }
    @GetMapping("/categories") public List<String> categories() { return service.categories(); }
    @GetMapping("/{id}") public ProductResponse byId(@PathVariable Long id,
            @RequestParam(required = false) String branchCode) { return service.byId(id, branchCode); }
    @GetMapping("/{id}/availability") public List<AvailabilityResponse> compare(@PathVariable Long id) { return service.comparison(id); }
    @GetMapping("/sku/{sku}/availability") public AvailabilityResponse availability(@PathVariable String sku,
            @RequestParam String branchCode) { return service.availability(sku, branchCode); }
}
