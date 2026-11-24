package cl.duoc.pedidos360.analytics.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import java.net.URI;
@Configuration
public class DynamoConfig {
 @Bean DynamoDbClient dynamo(@Value("${analytics.dynamodb-endpoint:}") String endpoint,
                             @Value("${analytics.aws-region:us-east-1}") String region){
   var b=DynamoDbClient.builder().region(Region.of(region));
   if(!endpoint.isBlank())b.endpointOverride(URI.create(endpoint))
       .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local","local")));
   return b.build();
 }
}
