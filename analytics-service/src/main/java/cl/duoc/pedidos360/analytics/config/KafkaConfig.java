package cl.duoc.pedidos360.analytics.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
@Configuration
public class KafkaConfig {
 @Bean NewTopic shoppingInteractions(){return TopicBuilder.name("shopping.interactions").partitions(1).replicas(1).build();}
}
