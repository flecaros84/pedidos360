package cl.duoc.pedidos360.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecommendationConsumerTests {
 @Test void materializesRecommendationsFromKafkaEvent() throws Exception {
   JdbcTemplate db=mock(JdbcTemplate.class);
   doReturn(List.of("Medicamentos")).when(db).query(
      contains("SELECT category"),any(RowMapper.class),eq("demo-user"));
   when(db.queryForList(contains("NOT IN"),eq("Medicamentos"),eq("demo-user")))
      .thenReturn(List.of(Map.of("sku","F002","score",7L)));
   String event=new ObjectMapper().writeValueAsString(Map.of(
      "eventId",UUID.randomUUID().toString(),
      "type","recommendations.recompute","userId","demo-user"));
   new RecommendationConsumer(db,new ObjectMapper()).recalculate(event);
   verify(db).update(startsWith("DELETE FROM analytics.user_recommendations"),eq("demo-user"));
   verify(db).update(startsWith("INSERT INTO analytics.user_recommendations"),
      eq("demo-user"),eq(1),eq("F002"),eq("Medicamentos"),eq(7L));
 }
}
