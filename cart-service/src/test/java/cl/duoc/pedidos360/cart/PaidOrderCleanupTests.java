package cl.duoc.pedidos360.cart;

import cl.duoc.pedidos360.cart.service.PaidOrderCleanup;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaidOrderCleanupTests {
    private static PaidOrderCleanup service(JdbcTemplate jdbc) {
        TransactionTemplate tx = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        return new PaidOrderCleanup(jdbc, tx);
    }

    @Test void decrementsOnlyPurchasedSkuAndDeduplicatesBranches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("INSERT INTO cart_paid_orders"), any(UUID.class))).thenReturn(1);
        UUID id = UUID.randomUUID();
        service(jdbc).apply(new PaidOrderCleanup.Request(id, "user1", List.of(
                new PaidOrderCleanup.Line("F001", 2), new PaidOrderCleanup.Line("F001", 1),
                new PaidOrderCleanup.Line("F002", 4))));
        verify(jdbc).update(startsWith("DELETE FROM cart_item"), eq("user1"), eq("F001"), eq(3));
        verify(jdbc).update(startsWith("UPDATE cart_item"), eq(3), eq("user1"), eq("F001"), eq(3));
        verify(jdbc).update(startsWith("DELETE FROM cart_item"), eq("user1"), eq("F002"), eq(4));
    }

    @Test void repeatedOrderNeverDebitsCartTwice() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(startsWith("INSERT INTO cart_paid_orders"), any(UUID.class))).thenReturn(0);
        service(jdbc).apply(new PaidOrderCleanup.Request(UUID.randomUUID(), "user1",
                List.of(new PaidOrderCleanup.Line("F001", 2))));
        verify(jdbc, never()).update(startsWith("DELETE FROM cart_item"), any(), any(), any());
        verify(jdbc, never()).update(startsWith("UPDATE cart_item"), any(), any(), any(), any());
    }
}
