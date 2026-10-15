package cl.duoc.pedidos360.orders.config;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Component
public class Identity {
    private final boolean local;
    public Identity(@Value("${app.security.mode:local}") String mode) { this.local="local".equals(mode); }
    public String user(Authentication authentication, HttpServletRequest request) {
        if (local) {
            String id=request.getHeader("X-Demo-User");
            if (id==null || !id.matches("[a-zA-Z0-9_-]{1,80}"))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"X-Demo-User inválido");
            return id;
        }
        Jwt jwt=jwt(authentication);
        String oid=jwt.getClaimAsString("oid");
        return oid!=null && !oid.isBlank()?oid:jwt.getSubject();
    }
    // El destinatario del pedido se define por la persona autenticada en el formulario;
    // el JWT se utiliza exclusivamente para identificar al propietario del pedido.
    public String recipient(String requestedEmail) {
        String email=requestedEmail==null?"":requestedEmail.trim();
        if (email.matches("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$")) return email;
        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
            "Ingresa un correo de notificación válido");
    }
    private static Jwt jwt(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken t) return t.getToken();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"JWT obligatorio");
    }
    public boolean local() { return local; }
}
