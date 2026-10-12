package cl.duoc.pedidos360.products.service;

import cl.duoc.pedidos360.products.api.ProductResponse;
import cl.duoc.pedidos360.products.domain.Product;
import cl.duoc.pedidos360.products.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductsService {

    private final ProductRepository repository;

    public ProductsService(ProductRepository repository) {
        this.repository = repository;
    }

    public List<ProductResponse> list(String search, String category) {
        String term = normalize(search, 120, "search");
        String selectedCategory = normalize(category, 80, "category");

        Specification<Product> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isTrue(root.get("active")));

            if (!term.isEmpty()) {
                // ESCAPE evita interpretar %, _ y \\ como comodines del usuario.
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.<String>get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("activeIngredient")), pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("laboratory")), pattern, '\\')
                ));
            }
            if (!selectedCategory.isEmpty()) {
                conditions.add(cb.equal(
                        cb.lower(root.<String>get("category")),
                        selectedCategory.toLowerCase(Locale.ROOT)
                ));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        return repository.findAll(filters, Sort.by("name", "sku"))
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse byId(Long id) {
        return repository.findByIdAndActiveTrue(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }

    public List<String> categories() {
        return repository.findActiveCategories();
    }

    private static String normalize(String value, int maxLength, String parameter) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El parámetro " + parameter + " supera " + maxLength + " caracteres");
        }
        return normalized;
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
