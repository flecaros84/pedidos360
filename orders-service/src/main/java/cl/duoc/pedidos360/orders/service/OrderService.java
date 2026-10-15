package cl.duoc.pedidos360.orders.service;
import cl.duoc.pedidos360.orders.api.*;
import cl.duoc.pedidos360.orders.client.CartClient;
import cl.duoc.pedidos360.orders.config.RabbitConfig;
import cl.duoc.pedidos360.orders.domain.*;
import cl.duoc.pedidos360.orders.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
@Service
public class OrderService {
    private final CartClient cart; private final OrderRepository orders;
    private final OutboxRepository outbox; private final ObjectMapper mapper;
    public OrderService(CartClient cart,OrderRepository orders,OutboxRepository outbox,ObjectMapper mapper) {
        this.cart=cart;this.orders=orders;this.outbox=outbox;this.mapper=mapper;
    }
    @Transactional
    public OrderResponse create(String userId,String email,String key,OrderRequest request,
                                String bearer,String demoUser) {
        var existing=orders.findByUserIdAndRequestKey(userId,key);
        if(existing.isPresent()) return OrderResponse.from(existing.get(),mapper);
        var quote=cart.quote(request.zoneCode(),bearer,demoUser);
        var option="FASTEST".equals(request.mode())?quote.fastest():quote.cheapest();
        if(option==null || option.shipments()==null || option.shipments().isEmpty()
            || option.total()==null || option.total().compareTo(BigDecimal.ZERO)<=0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Cotización sin productos disponibles");
        if(!request.zoneCode().equals(quote.zoneCode()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"La zona cotizada no coincide");
        var amount=option.medicinesTotal().add(option.shippingTotal());
        if(amount.compareTo(option.total())!=0)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Total de cotización inconsistente");
        for(var shipment:option.shipments()) {
            if(shipment.items()==null || shipment.items().isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Despacho sin productos");
            for(var line:shipment.items()) if(line.quantity()<=0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Cantidad inválida");
        }
        try {
            String snapshot=mapper.writeValueAsString(option);
            CustomerOrder order=orders.save(new CustomerOrder(userId,key,email,request.zoneCode(),
                    request.mode(),option.total(),snapshot));
            // Se guarda en la MISMA transacción que la orden; un fallo de Rabbit no la pierde.
            Map<String,Object> event=new LinkedHashMap<>();
            event.put("eventId",UUID.randomUUID().toString());
            event.put("type","order.created");
            event.put("orderId",order.getId().toString());
            event.put("recipientEmail",email); event.put("zoneCode",request.zoneCode());
            event.put("mode",request.mode()); event.put("status",order.getStatus());
            event.put("totalAmount",order.getTotalAmount());
            event.put("createdAt",order.getCreatedAt().toString());
            event.put("quote",option);
            outbox.save(new OutboxEvent(RabbitConfig.KEY,mapper.writeValueAsString(event)));
            return OrderResponse.from(order,mapper);
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el pedido",ex);
        }
    }
    @Transactional(readOnly=true)
    public List<OrderResponse> list(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(o->OrderResponse.from(o,mapper)).toList();
    }
    @Transactional(readOnly=true)
    public OrderResponse get(String userId,UUID id) {
        return orders.findByIdAndUserId(id,userId).map(o->OrderResponse.from(o,mapper))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Pedido no encontrado"));
    }
}
