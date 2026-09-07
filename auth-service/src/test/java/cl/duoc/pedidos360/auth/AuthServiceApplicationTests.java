package cl.duoc.pedidos360.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.security.mode=local")
@AutoConfigureMockMvc
class AuthServiceApplicationTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsLocalDemoUser() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("X-Demo-User", "test-user")
                        .header("X-Demo-Name", "Test User"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value("test-user"));
    }
}
