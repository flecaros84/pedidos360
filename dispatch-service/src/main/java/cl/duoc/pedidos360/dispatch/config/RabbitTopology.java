package cl.duoc.pedidos360.dispatch.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitTopology {
    @Bean DirectExchange businessExchange(){return new DirectExchange("orders.events",true,false);}
    @Bean Queue dispatchQueue(){return QueueBuilder.durable("dispatch.order-paid").build();}
    @Bean Binding dispatchBinding(Queue dispatchQueue,DirectExchange businessExchange){
        return BindingBuilder.bind(dispatchQueue).to(businessExchange).with("order.paid");
    }
}
