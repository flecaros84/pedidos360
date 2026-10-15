package cl.duoc.pedidos360.orders.api;
import cl.duoc.pedidos360.orders.domain.CustomerOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderResponse(UUID id,String recipientEmail,String zoneCode,String mode,String status,
    BigDecimal totalAmount,Instant createdAt,JsonNode quote) {
    public static OrderResponse from(CustomerOrder o,ObjectMapper mapper) {
        try {return new OrderResponse(o.getId(),o.getRecipientEmail(),o.getZoneCode(),o.getMode(),o.getStatus(),
            o.getTotalAmount(),o.getCreatedAt(),mapper.readTree(o.getQuoteJson()));}
        catch(Exception ex){throw new IllegalStateException("Snapshot de pedido inválido",ex);}
    }
}
