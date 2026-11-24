package cl.duoc.pedidos360.products.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class InventoryService {
    private final JdbcTemplate db; private final ObjectMapper json;
    public InventoryService(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}

    private static String sha256(byte[] content){
        try {byte[] hash=MessageDigest.getInstance("SHA-256").digest(content);
            return java.util.HexFormat.of().formatHex(hash);
        } catch(Exception e){throw new IllegalStateException(e);}
    }

    @Transactional
    public Map<String,Object> reserve(UUID orderId, JsonNode quote) {
        if(quote == null || !quote.path("shipments").isArray() || quote.path("shipments").isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cotización vacía");
        // A transaction-scoped advisory lock serializes conflicting requests for the same order.
        db.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))",orderId.toString());
        String canonical=quote.toString(); String hash=sha256(canonical.getBytes(StandardCharsets.UTF_8));
        List<Map<String,Object>> existing=db.queryForList(
                "SELECT payload_hash,status FROM catalogo.inventory_orders WHERE order_id=?",orderId);
        if(!existing.isEmpty()) {
            String prev=(String)existing.get(0).get("payload_hash");
            if(!prev.equals(hash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pedido ya reservado con otra cotización");
            return Map.of("orderId",orderId,"status",existing.get(0).get("status"));
        }
        db.update("INSERT INTO catalogo.inventory_orders(order_id,payload_hash,status) VALUES (?,?,'RESERVED')",orderId,hash);
        int count=0;
        for(JsonNode shipment:quote.path("shipments")){
            String branch=shipment.path("branchCode").asText();
            if(branch.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sucursal inválida");
            for(JsonNode item:shipment.path("items")){
                String sku=item.path("sku").asText(); int qty=item.path("quantity").asInt(0);
                if(sku.isBlank() || qty<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Producto inválido");
                int n=db.update("UPDATE catalogo.branch_inventory SET stock=stock-? WHERE branch_code=? " +
                         "AND product_id=(SELECT id FROM catalogo.products WHERE sku=?) AND stock>=?",qty,branch,sku,qty);
                if(n!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock insuficiente para "+sku+" en "+branch);
                db.update("INSERT INTO catalogo.inventory_reservations(order_id,branch_code,sku,quantity) " +
                          "VALUES (?,?,?,?) ON CONFLICT (order_id,branch_code,sku) DO UPDATE SET quantity=inventory_reservations.quantity + EXCLUDED.quantity",
                          orderId,branch,sku,qty);
                count+=qty;
            }
        }
        if(count==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Orden sin productos");
        return Map.of("orderId",orderId,"status","RESERVED","units",count);
    }

    @Transactional
    public void settle(UUID orderId,String action) {
        db.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))",orderId.toString());
        List<String> states=db.query("SELECT status FROM catalogo.inventory_orders WHERE order_id=?",
              (r,i)->r.getString(1),orderId);
        if(states.isEmpty()) throw new IllegalStateException("No existe reserva para orden "+orderId);
        String status=states.get(0);
        if(!"RESERVED".equals(status)) return; // Idempotent duplicate delivery.
        if("RELEASED".equals(action)) {
            db.query("SELECT branch_code,sku,quantity FROM catalogo.inventory_reservations WHERE order_id=?",
                rs->{
                    db.update("UPDATE catalogo.branch_inventory SET stock=stock+? WHERE branch_code=? " +
                        "AND product_id=(SELECT id FROM catalogo.products WHERE sku=?)",
                        rs.getInt("quantity"),rs.getString("branch_code"),rs.getString("sku"));
                },orderId);
        }
        db.update("UPDATE catalogo.inventory_orders SET status=? WHERE order_id=?",action,orderId);
        try {
            String eventType="COMMITTED".equals(action)?"inventory.committed":"inventory.released";
            Map<String,Object> event=Map.of("eventId",UUID.randomUUID().toString(),"type",eventType,
                  "orderId",orderId.toString(),"status",action);
            db.update("INSERT INTO catalogo.inventory_outbox(event_id,routing_key,payload) VALUES (?,?,?)",
                    UUID.fromString((String)event.get("eventId")),eventType,json.writeValueAsString(event));
        } catch(Exception ex){throw new IllegalStateException("Error de evento",ex);}
    }
}
