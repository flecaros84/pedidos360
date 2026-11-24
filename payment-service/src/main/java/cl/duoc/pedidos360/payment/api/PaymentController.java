package cl.duoc.pedidos360.payment.api;

import cl.duoc.pedidos360.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/payments")
public class PaymentController {
 private final PaymentService service;
 public PaymentController(PaymentService service){this.service=service;}
 public record Checkout(@NotNull UUID orderId,
       @NotNull @Pattern(regexp="SIMULATED|WEBPAY") String method,
       String cardReference, Boolean approved) {}

 @PostMapping public Map<String,Object> create(@Valid @RequestBody Checkout checkout,
     @RequestHeader("Idempotency-Key") String key,
     @RequestHeader(value="Authorization",required=false) String bearer,
     @RequestHeader(value="X-Demo-User",required=false) String demo) {
    return service.checkout(checkout,key,bearer,demo);
 }
 @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id,
     @RequestHeader(value="Authorization",required=false) String bearer,
     @RequestHeader(value="X-Demo-User",required=false) String demo) {
    return service.get(id,bearer,demo);
 }
 // Webpay returns an opaque token, never card data; accepted only after server-to-server commit.
 @RequestMapping(value="/webpay/return",method={RequestMethod.POST,RequestMethod.GET})
 public org.springframework.http.ResponseEntity<Void> webpayReturn(
     @RequestParam(value="token_ws",required=false) String token,
     @RequestParam(value="TBK_TOKEN",required=false) String cancelled) {
     boolean success=service.commitWebpay(token,cancelled);
     return org.springframework.http.ResponseEntity.status(303)
       .location(URI.create(service.resultRedirect(success))).build();
 }
}
