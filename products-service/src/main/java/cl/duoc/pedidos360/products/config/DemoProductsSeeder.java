package cl.duoc.pedidos360.products.config;

import cl.duoc.pedidos360.products.domain.Product;
import cl.duoc.pedidos360.products.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(1)
public class DemoProductsSeeder implements ApplicationRunner {

    private final ProductRepository repository;
    private final boolean seedDemo;

    public DemoProductsSeeder(ProductRepository repository,
                              @Value("${app.catalog.seed-demo:false}") boolean seedDemo) {
        this.repository = repository;
        this.seedDemo = seedDemo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedDemo) {
            return;
        }

        // Precios, laboratorio e inventario son datos ilustrativos de un prototipo.
        // Mantenemos los SKU F001-F006 del frontend v0.2.0 para facilitar la integración.
        List<Product> examples = List.of(
            product("F001", "Paracetamol 500 mg", "Paracetamol", "500 mg", "20 comprimidos", "Demostración", "Medicamentos", 1990),
            product("F002", "Ibuprofeno 200 mg", "Ibuprofeno", "200 mg", "10 comprimidos", "Demostración", "Medicamentos", 2490),
            product("F003", "Vitamina C 500 mg", "Ácido ascórbico", "500 mg", "30 comprimidos", "Demostración", "Bienestar", 2990),
            product("F004", "Suero fisiológico 0,9%", "Cloruro de sodio", "0,9%", "500 mL", "Demostración", "Cuidado personal", 1490),
            product("F005", "Alcohol gel 70%", null, "70%", "250 mL", "Demostración", "Cuidado personal", 1990),
            product("F006", "Termómetro digital", null, null, "1 unidad", "Demostración", "Insumos", 6990)
        );

        for (Product product : examples) {
            if (!repository.existsBySku(product.getSku())) {
                repository.save(product);
            }
        }
    }

    private Product product(String sku, String name, String ingredient, String strength,
                            String presentation, String laboratory, String category, int priceClp) {
        return new Product(sku, name, ingredient, strength, presentation, laboratory,
                category, BigDecimal.valueOf(priceClp), false);
    }
}
