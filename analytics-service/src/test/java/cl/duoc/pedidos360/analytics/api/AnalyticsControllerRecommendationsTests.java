package cl.duoc.pedidos360.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerRecommendationsTests {
 @Test void returnsMaterializedKafkaRecommendations() {
   JdbcTemplate db=mock(JdbcTemplate.class);
   when(db.queryForList(contains("analytics.user_recommendations"),eq("local-demo-user")))
      .thenReturn(List.of(Map.of("sku","F002","score",6L)));
   var result=new AnalyticsController(db).recommendations(null,"local-demo-user");
   assertThat(result).extracting(row->row.get("sku")).containsExactly("F002");
   verify(db).queryForList(contains("analytics.user_recommendations"),eq("local-demo-user"));
 }
 @Test void returnsEmptyBeforeFirstKafkaEvent() {
   JdbcTemplate db=mock(JdbcTemplate.class);
   when(db.queryForList(contains("analytics.user_recommendations"),eq("new-user"))).thenReturn(List.of());
   assertThat(new AnalyticsController(db).recommendations(null,"new-user")).isEmpty();
 }
}
