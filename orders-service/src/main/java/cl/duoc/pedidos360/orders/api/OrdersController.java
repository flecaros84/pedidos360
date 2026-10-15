package cl.duoc.pedidos360.orders.api;
import cl.duoc.pedidos360.orders.config.Identity;
import cl.duoc.pedidos360.orders.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/orders")
public class OrdersController {
    private final OrderService orders; private final Identity identity;
    public OrdersController(OrderService orders,Identity identity) {this.orders=orders;this.identity=identity;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody OrderRequest body,Authentication auth,HttpServletRequest request,
        @RequestHeader(name="Idempotency-Key",required=true) String key,
        @RequestHeader(name="Authorization",required=false) String bearer) {
        if(!key.matches("[A-Za-z0-9_-]{8,80}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key inválida");
        String demo=identity.local()?identity.user(auth,request):null;
        return orders.create(identity.user(auth,request),identity.recipient(body.recipientEmail()),
            key,body,bearer,demo);
    }
    @GetMapping public List<OrderResponse> list(Authentication auth,HttpServletRequest request) {
        return orders.list(identity.user(auth,request));
    }
    @GetMapping("/{id}") public OrderResponse get(@PathVariable UUID id,Authentication auth,HttpServletRequest request) {
        return orders.get(identity.user(auth,request),id);
    }
}
