package cl.duoc.pedidos360.audit.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/audit")
public class AuditController {
    private final JdbcTemplate db;
    public AuditController(JdbcTemplate db){this.db=db;}
    @GetMapping("/recent")
    public List<Map<String,Object>> recent(){
        return db.queryForList("SELECT event_id,event_type,source,received_at FROM audit.audit_events ORDER BY received_at DESC LIMIT 100");
    }
}
