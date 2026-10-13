package cl.duoc.pedidos360.products.api;

import cl.duoc.pedidos360.products.domain.ShippingRate;
import java.math.BigDecimal;
public record ShippingRateResponse(String branchCode, String branchName, String zoneCode,
                                   BigDecimal cost, int deliveryDays) {
    public static ShippingRateResponse from(ShippingRate rate) {
        return new ShippingRateResponse(rate.getBranch().getCode(), rate.getBranch().getName(),
                rate.getZoneCode(), rate.getCost(), rate.getDeliveryDays());
    }
}
