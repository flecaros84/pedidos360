package cl.duoc.pedidos360.auth.api;

import java.util.List;

public record UserInfoResponse(
        boolean authenticated,
        String userId,
        String name,
        List<String> scopes,
        List<String> roles) {
}
