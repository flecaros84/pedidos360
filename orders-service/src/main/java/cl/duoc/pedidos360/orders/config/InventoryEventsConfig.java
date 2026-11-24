package cl.duoc.pedidos360.orders.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class InventoryEventsConfig {
 @Bean Queue orderInventoryQueue(){return QueueBuilder.durable("orders.inventory-final").build();}
 @Bean Declarables orderInventoryBindings(Queue orderInventoryQueue,DirectExchange ordersExchange){
  return new Declarables(
     BindingBuilder.bind(orderInventoryQueue).to(ordersExchange).with("inventory.committed"),
     BindingBuilder.bind(orderInventoryQueue).to(ordersExchange).with("inventory.released"));
 }
}
