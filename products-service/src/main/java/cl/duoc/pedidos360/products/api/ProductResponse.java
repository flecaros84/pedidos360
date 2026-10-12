package cl.duoc.pedidos360.products.api;

import cl.duoc.pedidos360.products.domain.Product;

import java.math.BigDecimal;

/** Contrato público del catálogo, independiente de la entidad JPA. */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String activeIngredient,
        String strength,
        String presentation,
        String laboratory,
        String category,
        BigDecimal referencePrice,
        boolean requiresPrescription
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getActiveIngredient(),
                product.getStrength(),
                product.getPresentation(),
                product.getLaboratory(),
                product.getCategory(),
                product.getReferencePrice(),
                product.isRequiresPrescription()
        );
    }
}
