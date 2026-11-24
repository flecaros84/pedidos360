package cl.duoc.pedidos360.products.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class InventoryEventsConfig {
  @Bean DirectExchange inventoryBusinessExchange(){return new DirectExchange("orders.events",true,false);}
  @Bean Queue inventoryPaymentsQueue(){return QueueBuilder.durable("inventory.payment-results").build();}
  @Bean Declarables inventoryBindings(Queue inventoryPaymentsQueue,DirectExchange inventoryBusinessExchange){
    return new Declarables(
      BindingBuilder.bind(inventoryPaymentsQueue).to(inventoryBusinessExchange).with("payment.approved"),
      BindingBuilder.bind(inventoryPaymentsQueue).to(inventoryBusinessExchange).with("payment.rejected"));
  }
}
