package cl.duoc.pedidos360.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Component
public class RecommendationConsumer {
 private final JdbcTemplate db;
 private final ObjectMapper json;
 public RecommendationConsumer(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}

 @Transactional
 @KafkaListener(topics="recommendations.updated",groupId="recommendations")
 public void recalculate(String raw)throws Exception {
   JsonNode event=json.readTree(raw);
   if(!"recommendations.recompute".equals(event.path("type").asText()))return;
   String user=event.path("userId").asText("");
   if(user.isBlank()||user.equals("anonymous"))return;
   // Recalculate from the latest SQL projection; duplicates have the same result.
   List<String> categories=db.query("SELECT category FROM analytics.product_views WHERE user_id=? " +
     "AND category IS NOT NULL GROUP BY category ORDER BY SUM(views) DESC LIMIT 1",
     (rs,i)->rs.getString(1),user);
   db.update("DELETE FROM analytics.user_recommendations WHERE user_id=?",user);
   if(categories.isEmpty())return;
   String category=categories.get(0);
   List<Map<String,Object>> rows=db.queryForList("SELECT product_sku AS sku,SUM(views) AS score " +
     "FROM analytics.product_views WHERE category=? AND product_sku NOT IN " +
     "(SELECT product_sku FROM analytics.product_views WHERE user_id=?) " +
     "GROUP BY product_sku ORDER BY score DESC,product_sku LIMIT 6",category,user);
   if(rows.isEmpty()) rows=db.queryForList("SELECT product_sku AS sku,SUM(views) AS score " +
     "FROM analytics.product_views WHERE category=? GROUP BY product_sku " +
     "ORDER BY score DESC,product_sku LIMIT 6",category);
   int rank=1;
   for(var row:rows) {
     db.update("INSERT INTO analytics.user_recommendations(user_id,rank,product_sku,category,score) " +
       "VALUES (?,?,?,?,?)",user,rank++,row.get("sku"),category,((Number)row.get("score")).longValue());
   }
 }
}
