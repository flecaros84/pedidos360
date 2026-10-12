package cl.duoc.pedidos360.cart.api;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import cl.duoc.pedidos360.cart.security.CurrentUserService;
import cl.duoc.pedidos360.cart.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;
    private final CurrentUserService currentUserService;

    public CartController(CartService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CartItemResponse> list(Authentication auth, HttpServletRequest request) {
        return service.list(currentUserService.userId(auth, request));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse add(@Valid @RequestBody AddCartItemRequest body,
                                Authentication auth, HttpServletRequest request) {
        return service.add(currentUserService.userId(auth, request), body);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        service.delete(currentUserService.userId(auth, request), id);
    }
}
