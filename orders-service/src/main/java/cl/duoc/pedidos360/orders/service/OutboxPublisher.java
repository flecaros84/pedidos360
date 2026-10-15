package cl.duoc.pedidos360.orders.service;
import cl.duoc.pedidos360.orders.config.RabbitConfig;
import cl.duoc.pedidos360.orders.repository.OutboxRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
@Component
public class OutboxPublisher {
    private final OutboxRepository outbox; private final RabbitTemplate rabbit;
    private final TransactionTemplate transactions;
    public OutboxPublisher(OutboxRepository outbox,RabbitTemplate rabbit,TransactionTemplate transactions) {
        this.outbox=outbox;this.rabbit=rabbit;this.transactions=transactions;
    }
    @Scheduled(fixedDelayString="${app.outbox.poll-ms:5000}",initialDelay=5000)
    public void publish() {
        // Una instancia publicadora en v0.5.0; múltiples instancias requieren locking SKIP LOCKED.
        for(var event:outbox.findTop25ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                rabbit.invoke(operations -> {
                    operations.convertAndSend(RabbitConfig.EXCHANGE,event.getRoutingKey(),
                        event.getPayloadJson(), message -> {
                            message.getMessageProperties().setContentType("application/json");
                            message.getMessageProperties().setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                            return message;
                        });
                    operations.waitForConfirmsOrDie(5000);
                    return null;
                });
                transactions.executeWithoutResult(status -> {
                    outbox.findById(event.getId()).ifPresent(pending -> {pending.markPublished();outbox.save(pending);});
                });
            } catch(Exception ex) {
                // Registrar y reintentar; no eliminar ni marcar publicado sin confirmación.
                org.slf4j.LoggerFactory.getLogger(OutboxPublisher.class).warn(
                    "Evento {} pendiente: {}",event.getId(),ex.toString());
                return;
            }
        }
    }
}
