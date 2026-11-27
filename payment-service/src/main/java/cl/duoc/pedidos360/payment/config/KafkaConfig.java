package cl.duoc.pedidos360.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
 @Bean NewTopic fraudRequests(){return TopicBuilder.name("payments.fraud.requests").partitions(1).replicas(1).build();}
 @Bean NewTopic fraudDecisions(){return TopicBuilder.name("payments.fraud.decisions").partitions(1).replicas(1).build();}
}
