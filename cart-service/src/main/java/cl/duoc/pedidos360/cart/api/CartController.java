package cl.duoc.pedidos360.cart.api;
import cl.duoc.pedidos360.cart.security.CurrentUserService;
import cl.duoc.pedidos360.cart.service.CartService;
import cl.duoc.pedidos360.cart.quote.QuoteOptimizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/cart")
public class CartController {
    private final CartService service;
    private final CurrentUserService users;
    public CartController(CartService service, CurrentUserService users) { this.service = service; this.users = users; }
    @GetMapping public List<CartItemResponse> list(Authentication a, HttpServletRequest r) { return service.list(users.userId(a, r)); }
    @GetMapping("/quote") public QuoteOptimizer.Result quote(@RequestParam String zoneCode,
            Authentication a, HttpServletRequest r,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.quote(users.userId(a, r), zoneCode, authorization);
    }
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse add(@Valid @RequestBody AddCartItemRequest body, Authentication a, HttpServletRequest r,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return service.add(users.userId(a, r), body, authorization);
    }
    @DeleteMapping("/items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication a, HttpServletRequest r) { service.delete(users.userId(a, r), id); }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(Authentication a, HttpServletRequest r) { service.clear(users.userId(a, r)); }
}
