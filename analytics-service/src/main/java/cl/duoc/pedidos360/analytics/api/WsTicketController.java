package cl.duoc.pedidos360.analytics.api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
@RestController @RequestMapping("/api/analytics")
public class WsTicketController {
 private final DynamoDbClient db;private final String table;
 public WsTicketController(DynamoDbClient db,@Value("${analytics.ws-tickets-table}") String table){this.db=db;this.table=table;}
 @PostMapping("/ws-ticket") public Map<String,Object> issue(Authentication auth,
       @RequestHeader(value="X-Demo-User",required=false) String demo){
    String user=auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())?auth.getName():demo;
    if(user==null||user.isBlank())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sin usuario");
    String ticket=UUID.randomUUID().toString();
    long expire=Instant.now().plusSeconds(90).getEpochSecond();
    db.putItem(r->r.tableName(table).item(Map.of(
       "ticketId",AttributeValue.fromS(ticket),"userId",AttributeValue.fromS(user),
       "expiresAt",AttributeValue.fromN(Long.toString(expire)))));
    return Map.of("ticket",ticket,"expiresAt",expire);
 }
}
