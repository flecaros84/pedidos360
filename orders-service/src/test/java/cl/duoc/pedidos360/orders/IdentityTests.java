package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.orders.config.Identity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
class IdentityTests {
    @Test void destinatarioEsElCorreoIngresadoAunqueEsteAutenticadoPorAzure() {
        Identity identity=new Identity("azure");
        assertThat(identity.recipient(" destinatario@ejemplo.cl ")).isEqualTo("destinatario@ejemplo.cl");
    }
    @Test void tambienFuncionaEnModoLocal() {
        assertThat(new Identity("local").recipient("otro@ejemplo.cl")).isEqualTo("otro@ejemplo.cl");
    }
    @Test void noAdmiteDestinatariosVaciosONoValidos() {
        Identity identity=new Identity("azure");
        for (String email : new String[]{"", "correo-invalido", "incompleto@dominio"}) {
            assertThatThrownBy(()->identity.recipient(email))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex->assertThat(((ResponseStatusException)ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        }
        assertThatThrownBy(()->identity.recipient(null))
            .isInstanceOf(ResponseStatusException.class);
    }
}
