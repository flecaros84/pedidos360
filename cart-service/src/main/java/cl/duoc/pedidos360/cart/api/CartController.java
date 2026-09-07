package cl.duoc.pedidos360.cart.api;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import cl.duoc.pedidos360.cart.domain.CartItem;
import cl.duoc.pedidos360.cart.domain.CartItemRepository;
import cl.duoc.pedidos360.cart.security.CurrentUserService;

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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository repository;
    private final CurrentUserService currentUserService;

    public CartController(CartItemRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<CartItemResponse> list(Authentication authentication, HttpServletRequest request) {
        String userId = currentUserService.userId(authentication, request);
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(CartItemResponse::from)
                .toList();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItemResponse add(@Valid @RequestBody AddCartItemRequest body,
                                Authentication authentication,
                                HttpServletRequest request) {
        String userId = currentUserService.userId(authentication, request);
        CartItem item = new CartItem(userId, body.productCode(), body.productName(), body.quantity());
        return CartItemResponse.from(repository.save(item));
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       Authentication authentication,
                       HttpServletRequest request) {
        String userId = currentUserService.userId(authentication, request);
        CartItem item = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item no encontrado"));
        repository.delete(item);
    }
}
