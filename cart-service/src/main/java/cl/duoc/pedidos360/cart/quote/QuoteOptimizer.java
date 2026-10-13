package cl.duoc.pedidos360.cart.quote;

import cl.duoc.pedidos360.cart.client.ProductsClient.Availability;
import cl.duoc.pedidos360.cart.client.ProductsClient.ShippingRate;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Optimización determinista con 5 sucursales: evalúa los subconjuntos factibles (máx. 31).
 *  Se permite repartir las unidades de un SKU entre sucursales cuando no hay stock suficiente.
 *  No reserva inventario ni representa una promesa logística.
 */
public final class QuoteOptimizer {
    private QuoteOptimizer() {}

    public record RequestLine(String sku, String name, int quantity) {}
    public record AssignedLine(String sku, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    public record Shipment(String branchCode, String branchName, BigDecimal shippingCost,
                           int deliveryDays, List<AssignedLine> items) {}
    public record Option(String mode, BigDecimal medicinesTotal, BigDecimal shippingTotal,
                         BigDecimal total, int estimatedDays, List<Shipment> shipments) {}
    public record Result(String zoneCode, Option cheapest, Option fastest, String disclaimer) {}

    public static Result quote(String zoneCode, List<RequestLine> lines,
                               List<Availability> offers, List<ShippingRate> rates) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("Carrito vacío");
        if (rates == null || rates.isEmpty()) throw new IllegalArgumentException("No existen tarifas para la zona");
        Map<String, ShippingRate> byBranch = rates.stream().collect(Collectors.toMap(
            ShippingRate::branchCode, r -> r, (a,b) -> a, TreeMap::new));
        List<String> codes = new ArrayList<>(byBranch.keySet());
        if (codes.size() > 5) throw new IllegalArgumentException("Máximo de cinco sucursales");
        Map<String,List<Availability>> bySku = offers.stream()
            .filter(o -> o.price() != null && o.price().signum() >= 0 && o.stock() > 0)
            .collect(Collectors.groupingBy(Availability::sku));
        List<Option> candidates = new ArrayList<>();
        for (int subset = 1; subset < (1 << codes.size()); subset++) {
            Set<String> chosen = new HashSet<>();
            for (int bit = 0; bit < codes.size(); bit++) if ((subset & (1 << bit)) != 0) chosen.add(codes.get(bit));
            Map<String, List<AssignedLine>> assigned = new TreeMap<>();
            BigDecimal medicines = BigDecimal.ZERO;
            boolean feasible = true;
            for (RequestLine line : lines) {
                if (line.quantity() <= 0) throw new IllegalArgumentException("Cantidad inválida");
                int remaining = line.quantity();
                List<Availability> options = bySku.getOrDefault(line.sku(), List.of()).stream()
                    .filter(o -> chosen.contains(o.branchCode())).sorted(Comparator
                        .comparing(Availability::price)
                        .thenComparingInt(o -> byBranch.get(o.branchCode()).deliveryDays())
                        .thenComparing(Availability::branchCode)).toList();
                for (Availability offer : options) {
                    if (remaining == 0) break;
                    int taken = Math.min(remaining, offer.stock());
                    if (taken <= 0) continue;
                    BigDecimal subtotal = offer.price().multiply(BigDecimal.valueOf(taken));
                    medicines = medicines.add(subtotal);
                    assigned.computeIfAbsent(offer.branchCode(), ignored -> new ArrayList<>())
                        .add(new AssignedLine(line.sku(), line.name(), taken, offer.price(), subtotal));
                    remaining -= taken;
                }
                if (remaining > 0) { feasible = false; break; }
            }
            if (!feasible) continue;
            BigDecimal shipping = BigDecimal.ZERO;
            int days = 0;
            List<Shipment> shipments = new ArrayList<>();
            for (var entry : assigned.entrySet()) {
                ShippingRate rate = byBranch.get(entry.getKey());
                shipping = shipping.add(rate.cost());
                days = Math.max(days, rate.deliveryDays());
                shipments.add(new Shipment(rate.branchCode(), rate.branchName(), rate.cost(),
                    rate.deliveryDays(), List.copyOf(entry.getValue())));
            }
            candidates.add(new Option("", medicines, shipping, medicines.add(shipping), days, List.copyOf(shipments)));
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("Stock insuficiente para completar el carrito");
        Comparator<Option> cheapestComparator = Comparator.comparing(Option::total)
            .thenComparingInt(Option::estimatedDays).thenComparingInt(o -> o.shipments().size());
        Comparator<Option> fastestComparator = Comparator.comparingInt(Option::estimatedDays)
            .thenComparing(Option::total).thenComparingInt(o -> o.shipments().size());
        Option cheap = candidates.stream().min(cheapestComparator).orElseThrow();
        Option fast = candidates.stream().min(fastestComparator).orElseThrow();
        return new Result(zoneCode, mode(cheap, "CHEAPEST"), mode(fast, "FASTEST"),
            "Cotización simulada sin reserva de stock. Tarifas y plazos referenciales, sujetos a cambios antes del checkout.");
    }
    private static Option mode(Option source, String mode) {
        return new Option(mode, source.medicinesTotal(), source.shippingTotal(), source.total(),
            source.estimatedDays(), source.shipments());
    }
}
