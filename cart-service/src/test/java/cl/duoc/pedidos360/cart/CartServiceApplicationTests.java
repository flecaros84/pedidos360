package cl.duoc.pedidos360.cart;

import cl.duoc.pedidos360.cart.client.ProductsClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartServiceApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    // El CI no depende de un Products Service ni de una instancia AWS activa.
    @MockitoBean
    private ProductsClient productsClient;

    @BeforeEach
    void configureProducts() {
        when(productsClient.offers(anyList(), nullable(String.class)))
            .thenReturn(List.of(new ProductsClient.Availability(
                "PM-CENTRO", "Puerto Montt Centro", "F001",
                "Paracetamol 500 mg", new BigDecimal("1990"), 10
            )));
    }

    @Test
    void addsAndListsItemForLocalUser() throws Exception {
        String userId = "test-user-" + UUID.randomUUID();

        mockMvc.perform(post("/api/cart/items")
                .header("X-Demo-User", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "productCode": "F001",
                      "productName": "Nombre no oficial",
                      "quantity": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.productCode").value("F001"))
            .andExpect(jsonPath("$.productName").value("Paracetamol 500 mg"))
            .andExpect(jsonPath("$.quantity").value(1));

        mockMvc.perform(get("/api/cart").header("X-Demo-User", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productCode").value("F001"))
            .andExpect(jsonPath("$[0].productName").value("Paracetamol 500 mg"))
            .andExpect(jsonPath("$[0].quantity").value(1));
    }
}
