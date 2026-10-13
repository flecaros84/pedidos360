package cl.duoc.pedidos360.cart;

import cl.duoc.pedidos360.cart.client.ProductsClient;
// Agregar
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CartAggregationTests {
    @Autowired MockMvc mvc;
    @MockitoBean ProductsClient products;
    @org.junit.jupiter.api.BeforeEach
    void setup() {
        when(products.offers(anyList(), nullable(String.class))).thenAnswer(call -> {
            java.util.List<String> skus = call.getArgument(0);
            return skus.stream().map(sku -> new ProductsClient.Availability(
                "PM-CENTRO", "Puerto Montt Centro", sku, "Producto prueba", BigDecimal.valueOf(1000), 50)).toList();
        });
        when(products.shipping(org.mockito.ArgumentMatchers.anyString(), nullable(String.class)))
            .thenReturn(java.util.List.of(new ProductsClient.ShippingRate(
                "PM-CENTRO", "Puerto Montt Centro", "PM", BigDecimal.valueOf(1000), 1)));
    }
    @Autowired ObjectMapper mapper;

    private JsonNode add(String user, String code, int quantity) throws Exception {
        String payload = mapper.writeValueAsString(new Request(code, "Producto prueba", quantity));
        String result = mvc.perform(post("/api/cart/items")
                .header("X-Demo-User", user)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(result);
    }

    record Request(String productCode, String productName, int quantity) { }

    @Test
    void addsQuantitiesWithoutCreatingDuplicateLines() throws Exception {
        String user = "merge-" + UUID.randomUUID();
        JsonNode first = add(user, "F001", 2);
        JsonNode second = add(user, "F001", 3);
        assertThat(second.path("id").asLong()).isEqualTo(first.path("id").asLong());
        assertThat(second.path("quantity").asInt()).isEqualTo(5);

        String json = mvc.perform(get("/api/cart").header("X-Demo-User", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = mapper.readTree(json);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("quantity").asInt()).isEqualTo(5);
    }

    @Test
    void quoteIncludesShippingAndDoesNotReserveStock() throws Exception {
        String user = "quote-" + UUID.randomUUID();
        add(user, "F001", 2);
        String json = mvc.perform(get("/api/cart/quote").param("zoneCode", "PM")
            .header("X-Demo-User", user)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode result = mapper.readTree(json);
        assertThat(result.path("cheapest").path("total").asInt()).isEqualTo(3000);
        assertThat(result.path("fastest").path("estimatedDays").asInt()).isEqualTo(1);
    }

    @Test
    void quantitiesAreScopedToUserAndDeleteKeepsOthersIntact() throws Exception {
        String user1 = "owner-" + UUID.randomUUID();
        String user2 = "other-" + UUID.randomUUID();
        JsonNode item1 = add(user1, "F002", 2);
        JsonNode item2 = add(user2, "F002", 1);
        assertThat(item2.path("id").asLong()).isNotEqualTo(item1.path("id").asLong());
        mvc.perform(delete("/api/cart/items/" + item1.path("id").asLong())
                        .header("X-Demo-User", user2))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/" + item1.path("id").asLong())
                        .header("X-Demo-User", user1))
                .andExpect(status().isNoContent());
        String json = mvc.perform(get("/api/cart").header("X-Demo-User", user2))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(json).get(0).path("quantity").asInt()).isEqualTo(1);
    }
}
