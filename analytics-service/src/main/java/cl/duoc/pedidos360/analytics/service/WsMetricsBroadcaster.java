package cl.duoc.pedidos360.analytics.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
@Component
public class WsMetricsBroadcaster {
 private final JdbcTemplate jdbc;private final ObjectMapper json;private final DynamoDbClient dynamo;
 private final ApiGatewayManagementApiClient gateway;private final String connectionsTable;
 public WsMetricsBroadcaster(JdbcTemplate jdbc,ObjectMapper json,DynamoDbClient dynamo,
       @Value("${analytics.ws-management-endpoint:}") String endpoint,
       @Value("${analytics.aws-region:us-east-1}") String region,
       @Value("${analytics.ws-connections-table}") String connectionsTable){
    this.jdbc=jdbc;this.json=json;this.dynamo=dynamo;this.connectionsTable=connectionsTable;
    this.gateway=endpoint.isBlank()?null:ApiGatewayManagementApiClient.builder()
       .region(Region.of(region)).endpointOverride(URI.create(endpoint)).build();
 }
 @Scheduled(fixedDelay=5000,initialDelay=20000)
 public void push(){
    if(gateway==null)return;
    try {
        Map<String,Object> metrics=new LinkedHashMap<>();
        jdbc.query("SELECT name,value FROM analytics.analytics_metrics",rs->{metrics.put(rs.getString(1),rs.getLong(2));});
        metrics.put("topProducts",jdbc.queryForList("SELECT product_sku AS sku,SUM(views) AS views FROM analytics.product_views GROUP BY product_sku ORDER BY views DESC LIMIT 5"));
        metrics.put("activeProducts",jdbc.queryForObject("SELECT COUNT(DISTINCT product_sku) FROM analytics.product_views",Long.class));
        String payload=json.writeValueAsString(Map.of("type","analytics.metrics","data",metrics));
        var items=dynamo.scan(r->r.tableName(connectionsTable).limit(100)).items();
        for(var item:items){
           String id=item.get("connectionId").s();
           try {gateway.postToConnection(r->r.connectionId(id).data(SdkBytes.fromUtf8String(payload)));}
           catch(GoneException gone){dynamo.deleteItem(r->r.tableName(connectionsTable).key(Map.of("connectionId",AttributeValue.fromS(id))));}
        }
    } catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("WebSocket métricas: {}",ex.toString());}
 }
}
