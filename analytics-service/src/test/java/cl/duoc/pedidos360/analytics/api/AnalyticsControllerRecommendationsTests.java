package cl.duoc.pedidos360.analytics.api;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerRecommendationsTests {
    @Test
    void recommendsUnseenProductsFirst() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        doReturn(List.of("Medicamentos")).when(db)
                .query(anyString(), any(RowMapper.class), eq("local-demo-user"));
        when(db.queryForList(contains("NOT IN"), eq("Medicamentos"), eq("local-demo-user")))
                .thenReturn(List.of(Map.of("sku", "F002", "score", 6L)));

        var result = new AnalyticsController(db).recommendations(null, "local-demo-user");

        assertThat(result).extracting(row -> row.get("sku")).containsExactly("F002");
        verify(db, never()).queryForList(contains("WHERE category=? GROUP BY"), eq("Medicamentos"));
    }

    @Test
    void fallsBackToPopularProductsWhenAllInCategoryWereViewed() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        doReturn(List.of("Medicamentos")).when(db)
                .query(anyString(), any(RowMapper.class), eq("local-demo-user"));
        when(db.queryForList(contains("NOT IN"), eq("Medicamentos"), eq("local-demo-user")))
                .thenReturn(List.of());
        when(db.queryForList(contains("WHERE category=? GROUP BY"), eq("Medicamentos")))
                .thenReturn(List.of(Map.of("sku", "F002", "score", 6L)));

        var result = new AnalyticsController(db).recommendations(null, "local-demo-user");

        assertThat(result).extracting(row -> row.get("sku")).containsExactly("F002");
    }

    @Test
    void doesNotRecommendWithoutBrowsingHistory() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        doReturn(List.of()).when(db).query(anyString(), any(RowMapper.class), eq("new-user"));

        var result = new AnalyticsController(db).recommendations(null, "new-user");

        assertThat(result).isEmpty();
    }
}
