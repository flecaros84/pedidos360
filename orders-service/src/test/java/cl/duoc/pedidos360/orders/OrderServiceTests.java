package cl.duoc.pedidos360.orders;
import cl.duoc.pedidos360.orders.api.*;
import cl.duoc.pedidos360.orders.client.CartClient;
import cl.duoc.pedidos360.orders.domain.*;
import cl.duoc.pedidos360.orders.repository.*;
import cl.duoc.pedidos360.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class OrderServiceTests {
    @Test void guardaPedidoYEventoOutboxEnUnaOperacion() {
        CartClient cart=mock(CartClient.class);
        OrderRepository orders=mock(OrderRepository.class);
        OutboxRepository events=mock(OutboxRepository.class);
        ObjectMapper json=new ObjectMapper().findAndRegisterModules();
        var items=List.of(new CartClient.Line("F001","Paracetamol",2,
            new BigDecimal("100"),new BigDecimal("200")));
        var shipments=List.of(new CartClient.Shipment("PM","Puerto Montt",new BigDecimal("50"),2,items));
        var option=new CartClient.Option("CHEAPEST",new BigDecimal("200"),new BigDecimal("50"),
            new BigDecimal("250"),2,shipments);
        when(orders.findByUserIdAndRequestKey(anyString(),anyString())).thenReturn(Optional.empty());
        when(cart.quote(eq("PM"),isNull(),eq("demo")))
            .thenReturn(new CartClient.Quote("PM",option,option,"simulado"));
        when(orders.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new OrderService(cart,orders,events,json);
        var result=service.create("demo","demo@example.cl","idempotency123",
            new OrderRequest("PM","CHEAPEST","demo@example.cl"),null,"demo");
        assertThat(result.totalAmount()).isEqualByComparingTo("250");
        assertThat(result.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(result.recipientEmail()).isEqualTo("demo@example.cl");
        assertThat(result.quote().path("shipments").size()).isEqualTo(1);
        ArgumentCaptor<OutboxEvent> event=ArgumentCaptor.forClass(OutboxEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().getPayloadJson()).contains("order.created").contains("demo@example.cl");
    }
    @Test void reintentoMismaClaveNoDuplicaPedido() {
        CartClient cart=mock(CartClient.class);
        OrderRepository orders=mock(OrderRepository.class);
        OutboxRepository events=mock(OutboxRepository.class);
        var saved=new CustomerOrder("demo","same-key-123","demo@example.cl","PM","CHEAPEST",
            BigDecimal.TEN,"{\"shipments\":[]}");
        when(orders.findByUserIdAndRequestKey("demo","same-key-123")).thenReturn(Optional.of(saved));
        var result=new OrderService(cart,orders,events,new ObjectMapper().findAndRegisterModules()).create("demo","demo@example.cl",
           "same-key-123",new OrderRequest("PM","CHEAPEST",null),null,"demo");
        assertThat(result.id()).isEqualTo(saved.getId());
        verifyNoInteractions(cart,events);
    }
}
