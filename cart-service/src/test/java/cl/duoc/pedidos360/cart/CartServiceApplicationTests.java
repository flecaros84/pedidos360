package cl.duoc.pedidos360.cart;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CartServiceApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void addsAndListsItemForLocalUser() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("X-Demo-User", "test-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode": "P001",
                                  "productName": "Notebook",
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productCode").value("P001"));

        mockMvc.perform(get("/api/cart").header("X-Demo-User", "test-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("Notebook"));
    }
}
