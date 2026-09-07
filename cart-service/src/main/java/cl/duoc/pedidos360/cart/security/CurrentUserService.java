package cl.duoc.pedidos360.cart.security;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CurrentUserService {

    private final String securityMode;

    public CurrentUserService(@Value("${app.security.mode:local}") String securityMode) {
        this.securityMode = securityMode;
    }

    public String userId(Authentication authentication, HttpServletRequest request) {
        if ("local".equalsIgnoreCase(securityMode)) {
            return Optional.ofNullable(request.getHeader("X-Demo-User"))
                    .filter(StringUtils::hasText)
                    .orElse("local-demo-user");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Optional.ofNullable(jwt.getClaimAsString("oid"))
                .filter(StringUtils::hasText)
                .orElse(jwt.getSubject());
    }
}
