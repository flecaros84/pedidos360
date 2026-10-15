package cl.duoc.pedidos360.orders.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE="orders.events";
    public static final String KEY="order.created";
    public static final String QUEUE="notifications.order-created";
    public static final String DLX="orders.dlx";
    public static final String DLQ="notifications.order-created.dlq";
    @Bean DirectExchange ordersExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean DirectExchange deadExchange() { return new DirectExchange(DLX, true, false); }
    @Bean Queue orderQueue() { return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX)
        .deadLetterRoutingKey("order.created.failed").build(); }
    @Bean Queue deadQueue() { return QueueBuilder.durable(DLQ).build(); }
    @Bean Binding orderBinding(Queue orderQueue, DirectExchange ordersExchange) {
        return BindingBuilder.bind(orderQueue).to(ordersExchange).with(KEY);
    }
    @Bean Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) {
        return BindingBuilder.bind(deadQueue).to(deadExchange).with("order.created.failed");
    }
}
