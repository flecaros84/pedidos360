package cl.duoc.pedidos360.products.api;

import cl.duoc.pedidos360.products.service.ProductsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductsController {

    private final ProductsService productsService;

    public ProductsController(ProductsService productsService) {
        this.productsService = productsService;
    }

    @GetMapping
    public List<ProductResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        return productsService.list(search, category);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return productsService.categories();
    }

    @GetMapping("/{id}")
    public ProductResponse byId(@PathVariable Long id) {
        return productsService.byId(id);
    }
}
