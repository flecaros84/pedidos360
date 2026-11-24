package cl.duoc.pedidos360.audit.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitTopology {
    @Bean DirectExchange businessEvents(){return new DirectExchange("orders.events",true,false);}
    @Bean Queue auditQueue(){return QueueBuilder.durable("audit.all-events").build();}
    @Bean Declarables auditBindings(Queue auditQueue, DirectExchange businessEvents){
        return new Declarables(
          BindingBuilder.bind(auditQueue).to(businessEvents).with("order.created"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("order.paid"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("payment.approved"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("payment.rejected"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("payment.blocked"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("inventory.committed"),
          BindingBuilder.bind(auditQueue).to(businessEvents).with("inventory.released"));
    }
}
