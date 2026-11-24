package cl.duoc.pedidos360.payment.service;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class PaymentOutboxPublisher {
 private final JdbcTemplate db; private final RabbitTemplate rabbit;
 public PaymentOutboxPublisher(JdbcTemplate db,RabbitTemplate rabbit){this.db=db;this.rabbit=rabbit;}
 @Scheduled(fixedDelay=5000,initialDelay=10000)
 public void publish(){
   var items=db.queryForList("SELECT id,routing_key,body FROM payments.payment_events WHERE published_at IS NULL ORDER BY created_at LIMIT 25");
   for(var row:items) try {
      rabbit.invoke(op->{op.convertAndSend("orders.events",(String)row.get("routing_key"),(String)row.get("body"));op.waitForConfirmsOrDie(5000);return null;});
      db.update("UPDATE payments.payment_events SET published_at=NOW() WHERE id=?",(UUID)row.get("id"));
   } catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Pago pendiente de Rabbit: {}",ex.toString());break;}
 }
}
