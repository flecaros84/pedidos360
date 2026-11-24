package cl.duoc.pedidos360.notifications.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declare the consumed queue in this service, independent of Orders startup order. */
@Configuration
public class RabbitConfig {
    private static final String ORDERS_EXCHANGE = "orders.events";
    private static final String DEAD_EXCHANGE = "orders.dlx";
    private static final String ORDER_PAID = "order.paid";
    private static final String QUEUE = "notifications.order-paid";
    private static final String DEAD_QUEUE = "notifications.order-paid.dlq";

    @Bean
    DirectExchange ordersExchange() {
        return new DirectExchange(ORDERS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange ordersDeadLetterExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey("order.paid.failed")
                .build();
    }

    @Bean
    Queue notificationsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding notificationsBinding(Queue notificationsQueue, DirectExchange ordersExchange) {
        return BindingBuilder.bind(notificationsQueue).to(ordersExchange).with(ORDER_PAID);
    }

    @Bean
    Binding notificationsDeadLetterBinding(Queue notificationsDeadLetterQueue,
                                          DirectExchange ordersDeadLetterExchange) {
        return BindingBuilder.bind(notificationsDeadLetterQueue)
                .to(ordersDeadLetterExchange).with("order.paid.failed");
    }
}
