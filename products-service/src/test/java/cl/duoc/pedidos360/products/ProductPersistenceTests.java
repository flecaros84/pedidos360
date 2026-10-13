package cl.duoc.pedidos360.products;

import cl.duoc.pedidos360.products.config.DemoProductsSeeder;
import cl.duoc.pedidos360.products.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductPersistenceTests {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private DemoProductsSeeder seeder;

    @Test
    void cargaSeisProductosSinDuplicar() throws Exception {
        assertThat(repository.count()).isEqualTo(6);
        assertThat(repository.findBySku("F001")).isPresent();

        seeder.run(new DefaultApplicationArguments(new String[] {}));

        assertThat(repository.count()).isEqualTo(6);
    }
}
