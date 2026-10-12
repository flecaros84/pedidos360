package cl.duoc.pedidos360.products;

import cl.duoc.pedidos360.products.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductsApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProductRepository repository;

    @Test
    void listaSeisProductosConDatosFarmaceuticos() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].sku", hasItem("F001")));
    }

    @Test
    void consultaPorIdYResponde404CuandoNoExiste() throws Exception {
        Long id = repository.findBySku("F001").orElseThrow().getId();
        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("F001"))
                .andExpect(jsonPath("$.name").value("Paracetamol 500 mg"))
                .andExpect(jsonPath("$.referencePrice").value(1990));

        mockMvc.perform(get("/api/products/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscaSinDistinguirMayusculas() throws Exception {
        mockMvc.perform(get("/api/products").param("search", "PARACETAMOL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("F001"));
    }

    @Test
    void permiteCombinarCategoriaYBusqueda() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("category", "medicamentos")
                        .param("search", "ibuprofeno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("F002"));
    }

    @Test
    void devuelveCategoriasSinDuplicados() throws Exception {
        mockMvc.perform(get("/api/products/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*]", hasItem("Medicamentos")))
                .andExpect(jsonPath("$[*]", hasItem("Insumos")))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void parametroExcesivamenteLargoEsInvalido() throws Exception {
        mockMvc.perform(get("/api/products").param("search", "a".repeat(121)))
                .andExpect(status().isBadRequest());
    }
}
