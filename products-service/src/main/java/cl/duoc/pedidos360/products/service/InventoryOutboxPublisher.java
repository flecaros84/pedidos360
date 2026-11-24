package cl.duoc.pedidos360.products.service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class InventoryOutboxPublisher {
 private final JdbcTemplate db; private final RabbitTemplate rabbit;
 public InventoryOutboxPublisher(JdbcTemplate db,RabbitTemplate rabbit){this.db=db;this.rabbit=rabbit;}
 @Scheduled(fixedDelay=5000,initialDelay=10000)
 public void publish(){
    var batch=db.queryForList("SELECT event_id,routing_key,payload FROM catalogo.inventory_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 25");
    for(var row:batch) try {
      rabbit.invoke(op->{op.convertAndSend("orders.events",(String)row.get("routing_key"),(String)row.get("payload"));op.waitForConfirmsOrDie(5000);return null;});
      db.update("UPDATE catalogo.inventory_outbox SET published_at=NOW() WHERE event_id=?",(UUID)row.get("event_id"));
    } catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Evento inventario pendiente: {}",ex.toString());break;}
 }
}
