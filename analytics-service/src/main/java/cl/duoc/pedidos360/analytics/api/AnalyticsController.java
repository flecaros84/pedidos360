package cl.duoc.pedidos360.analytics.api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/analytics")
public class AnalyticsController {
 private final JdbcTemplate db;
 public AnalyticsController(JdbcTemplate db){this.db=db;}
 @GetMapping("/metrics") public Map<String,Object> metrics(){
    Map<String,Object> result=new LinkedHashMap<>();
    db.query("SELECT name,value FROM analytics.analytics_metrics",rs->{result.put(rs.getString(1),rs.getLong(2));});
    result.put("activeProducts",db.queryForObject("SELECT count(DISTINCT product_sku) FROM analytics.product_views",Long.class));
    result.put("topProducts",db.queryForList("SELECT product_sku AS sku,SUM(views) AS views FROM analytics.product_views GROUP BY product_sku ORDER BY views DESC LIMIT 5"));
    return result;
 }
 @GetMapping("/recommendations") public List<Map<String,Object>> recommendations(Authentication auth,
         @RequestHeader(value="X-Demo-User",required=false) String demo){
    String user=auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())?auth.getName():demo;
    if(user==null || user.isBlank()) return List.of();
    // Materialized by the recommendations Kafka consumer, never computed by this HTTP request.
    return db.queryForList("SELECT product_sku AS sku,score FROM analytics.user_recommendations " +
             "WHERE user_id=? ORDER BY rank LIMIT 6", user);
 }
}
