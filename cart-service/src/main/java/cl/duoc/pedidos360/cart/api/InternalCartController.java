package cl.duoc.pedidos360.cart.api;

import cl.duoc.pedidos360.cart.service.PaidOrderCleanup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Not exposed by the public API; requires an internal shared secret. */
@RestController
@RequestMapping("/internal/cart")
public class InternalCartController {
    private final PaidOrderCleanup cleanup;
    private final String serviceToken;

    public InternalCartController(PaidOrderCleanup cleanup,
            @Value("${INTERNAL_SERVICE_TOKEN:}") String serviceToken) {
        this.cleanup = cleanup;
        this.serviceToken = serviceToken;
    }

    @PostMapping("/paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void settled(@RequestHeader(value = "X-Service-Token", required = false) String supplied,
                        @RequestBody PaidOrderCleanup.Request request) {
        if (serviceToken.isBlank() || supplied == null || !MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                serviceToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal credential");
        }
        cleanup.apply(request);
    }
}
