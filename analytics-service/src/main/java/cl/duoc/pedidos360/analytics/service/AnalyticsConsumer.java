package cl.duoc.pedidos360.analytics.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import java.time.Instant;
import java.util.*;
@Component
public class AnalyticsConsumer {
 private final JdbcTemplate db;private final ObjectMapper json;private final DynamoDbClient dynamo;
 private final String table;
 public AnalyticsConsumer(JdbcTemplate db,ObjectMapper json,DynamoDbClient dynamo,
                          @Value("${analytics.dynamodb-table}") String table){
    this.db=db;this.json=json;this.dynamo=dynamo;this.table=table;
 }
 @KafkaListener(topics="shopping.interactions",groupId="analytics")
 public void consume(String raw) throws Exception {
   JsonNode event=json.readTree(raw);
   String type=event.path("type").asText();
   String id=event.path("eventId").asText();
   String user=event.path("userId").asText("anonymous");
   if(id.isBlank() || !(type.equals("product.view")||type.equals("cart.add")))return;
   // Idempotent event persistence in DynamoDB; duplicate messages are not counted again.
   Map<String,AttributeValue> item=new HashMap<>();
   item.put("eventId",AttributeValue.fromS(id));
   item.put("type",AttributeValue.fromS(type));
   item.put("userId",AttributeValue.fromS(user));
   item.put("payload",AttributeValue.fromS(raw));
   item.put("createdAt",AttributeValue.fromS(Instant.now().toString()));
   try {
     dynamo.putItem(PutItemRequest.builder().tableName(table).item(item)
          .conditionExpression("attribute_not_exists(eventId)").build());
   } catch(software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException duplicate){
       // Another attempt already persisted DynamoDB; still complete the SQL projection.
   }
   // If SQL fails, Kafka retries. A unique event ledger prevents double counting.
   int inserted=db.update("INSERT INTO analytics.processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING",UUID.fromString(id));
   if(inserted==0)return;
   db.update("INSERT INTO analytics.analytics_metrics(name,value) VALUES (?,1) ON CONFLICT(name) DO UPDATE SET value=analytics.analytics_metrics.value+1",type);
   if(type.equals("product.view")) {
      String sku=event.path("sku").asText();
      if(!sku.isBlank() && !user.isBlank())
        db.update("INSERT INTO analytics.product_views(user_id,product_sku,category,views) VALUES (?,?,?,1) " +
            "ON CONFLICT (user_id,product_sku) DO UPDATE SET views=analytics.product_views.views+1,last_view=NOW()",
            user,sku,event.path("category").asText(""));
   }
 }
}
