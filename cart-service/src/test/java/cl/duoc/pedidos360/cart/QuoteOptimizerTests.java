package cl.duoc.pedidos360.cart;

import cl.duoc.pedidos360.cart.client.ProductsClient.Availability;
import cl.duoc.pedidos360.cart.client.ProductsClient.ShippingRate;
import cl.duoc.pedidos360.cart.quote.QuoteOptimizer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class QuoteOptimizerTests {
    private static BigDecimal clp(int price) { return BigDecimal.valueOf(price); }
    @Test void cotizaPrecioTotalYEntregaMasRapidaConCriteriosDistintos() {
        var items = List.of(new QuoteOptimizer.RequestLine("F001", "Paracetamol", 1));
        var offers = List.of(new Availability("A", "Sucursal A", "F001", "Paracetamol", clp(100), 2),
             new Availability("B", "Sucursal B", "F001", "Paracetamol", clp(170), 2));
        var rates = List.of(new ShippingRate("A", "Sucursal A", "PM", clp(20), 3),
             new ShippingRate("B", "Sucursal B", "PM", clp(10), 1));
        var result = QuoteOptimizer.quote("PM", items, offers, rates);
        assertThat(result.cheapest().total()).isEqualByComparingTo(clp(120));
        assertThat(result.cheapest().estimatedDays()).isEqualTo(3);
        assertThat(result.fastest().total()).isEqualByComparingTo(clp(180));
        assertThat(result.fastest().estimatedDays()).isEqualTo(1);
    }
    @Test void permiteRepartirUnSkuEntreDosSucursalesPorStock() {
        var items = List.of(new QuoteOptimizer.RequestLine("F001", "Paracetamol", 3));
        var offers = List.of(new Availability("A", "A", "F001", "Paracetamol", clp(100), 2),
             new Availability("B", "B", "F001", "Paracetamol", clp(150), 1));
        var rates = List.of(new ShippingRate("A", "A", "PM", clp(30), 2),
             new ShippingRate("B", "B", "PM", clp(40), 1));
        var result = QuoteOptimizer.quote("PM", items, offers, rates);
        assertThat(result.cheapest().shipments()).hasSize(2);
        assertThat(result.cheapest().total()).isEqualByComparingTo(clp(420));
        assertThat(result.cheapest().shipments().stream()
            .flatMap(s -> s.items().stream()).mapToInt(QuoteOptimizer.AssignedLine::quantity).sum()).isEqualTo(3);
    }
    @Test void noCotizaCuandoNoHayStockSuficiente() {
        var item = List.of(new QuoteOptimizer.RequestLine("F001", "Paracetamol", 10));
        var offers = List.of(new Availability("A", "A", "F001", "Paracetamol", clp(100), 2));
        var rates = List.of(new ShippingRate("A", "A", "PM", clp(10), 1));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> QuoteOptimizer.quote("PM", item, offers, rates))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Stock insuficiente");
    }
}
