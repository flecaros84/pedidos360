package cl.duoc.pedidos360.dispatch.api;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController @RequestMapping("/api/dispatch")
public class DispatchController {
    private final JdbcTemplate db;
    public DispatchController(JdbcTemplate db){this.db=db;}
    @GetMapping public List<Map<String,Object>> list(){
       return db.queryForList("SELECT order_id,status,created_at,updated_at FROM dispatch.shipments ORDER BY created_at DESC LIMIT 100");
    }
    @PatchMapping("/{id}/status") public Map<String,Object> update(@PathVariable UUID id,
       @RequestBody Map<String,String> body){
       String state=body.getOrDefault("status","");
       if(!List.of("PREPARING","SHIPPED","DELIVERED").contains(state))
           throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Estado inválido");
       int count=db.update("UPDATE dispatch.shipments SET status=?,updated_at=NOW() WHERE order_id=?",state,id);
       if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Despacho no encontrado");
       return Map.of("orderId",id,"status",state);
    }
}
