package cl.duoc.pedidos360.products.api;
import cl.duoc.pedidos360.products.service.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/internal/inventory")
public class InventoryInternalController {
    private final InventoryService inventory; private final String token;
    public InventoryInternalController(InventoryService inventory,@Value("${app.internal.token:}") String token){
        this.inventory=inventory;this.token=token;
    }
    public record Reservation(UUID orderId,JsonNode quote){}
    private void check(String header){
        if(token.isBlank() || header==null ||
            !MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
           throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Servicio no autorizado");
    }
    @PostMapping("/reserve") public Map<String,Object> reserve(@RequestBody Reservation req,
            @RequestHeader(name="X-Service-Token",required=false) String header){
        check(header); return inventory.reserve(req.orderId(),req.quote());
    }
}
