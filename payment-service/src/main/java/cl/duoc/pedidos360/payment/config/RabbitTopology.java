package cl.duoc.pedidos360.payment.config;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitTopology {
 @Bean DirectExchange businessEvents(){return new DirectExchange("orders.events",true,false);}
}
