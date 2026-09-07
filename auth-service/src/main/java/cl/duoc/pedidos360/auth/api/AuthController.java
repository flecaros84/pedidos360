package cl.duoc.pedidos360.auth.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final String securityMode;

    public AuthController(@Value("${app.security.mode:local}") String securityMode) {
        this.securityMode = securityMode;
    }

    @GetMapping("/me")
    public UserInfoResponse me(Authentication authentication, HttpServletRequest request) {
        if ("local".equalsIgnoreCase(securityMode)) {
            String userId = Optional.ofNullable(request.getHeader("X-Demo-User"))
                    .filter(StringUtils::hasText)
                    .orElse("local-demo-user");
            String name = Optional.ofNullable(request.getHeader("X-Demo-Name"))
                    .filter(StringUtils::hasText)
                    .orElse("Usuario Local");
            return new UserInfoResponse(true, userId, name, List.of("local.demo"), List.of("LOCAL_USER"));
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = Optional.ofNullable(jwt.getClaimAsString("oid"))
                .filter(StringUtils::hasText)
                .orElse(jwt.getSubject());
        String name = Optional.ofNullable(jwt.getClaimAsString("name"))
                .filter(StringUtils::hasText)
                .orElse(Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse(userId));

        List<String> scopes = parseScopes(jwt.getClaimAsString("scp"));
        List<String> roles = Optional.ofNullable(jwt.getClaimAsStringList("roles"))
                .orElse(Collections.emptyList());

        return new UserInfoResponse(true, userId, name, scopes, roles);
    }

    private List<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) return Collections.emptyList();
        return Arrays.stream(scopes.split("\\s+"))
                .filter(StringUtils::hasText)
                .toList();
    }
}
