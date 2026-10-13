package cl.duoc.pedidos360.products.service;

import cl.duoc.pedidos360.products.api.AvailabilityResponse;
import cl.duoc.pedidos360.products.api.ProductResponse;
import cl.duoc.pedidos360.products.domain.BranchInventory;
import cl.duoc.pedidos360.products.domain.Product;
import cl.duoc.pedidos360.products.repository.BranchInventoryRepository;
import cl.duoc.pedidos360.products.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @Transactional(readOnly = true)
public class ProductsService {
    private final ProductRepository repository;
    private final BranchInventoryRepository inventory;
    private final BranchesService branches;
    public ProductsService(ProductRepository repository, BranchInventoryRepository inventory, BranchesService branches) {
        this.repository = repository; this.inventory = inventory; this.branches = branches;
    }
    public List<ProductResponse> list(String search, String category, String branchCode) {
        String term = normalize(search, 120, "search");
        String selected = normalize(category, 80, "category");
        Specification<Product> filters = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (!term.isEmpty()) {
                String pattern = "%" + escapeLike(term.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.<String>get("sku")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("activeIngredient")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("laboratory")), pattern, '\\')));
            }
            if (!selected.isEmpty()) predicates.add(cb.equal(cb.lower(root.<String>get("category")), selected.toLowerCase(Locale.ROOT)));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        var found = repository.findAll(filters, Sort.by("name", "sku"));
        if (branchCode == null || branchCode.isBlank()) {
            if (found.isEmpty()) return List.of();
            Map<String, List<BranchInventory>> bySku = inventory.findForSkus(found.stream().map(Product::getSku).toList())
                .stream().collect(Collectors.groupingBy(i -> i.getProduct().getSku()));
            return found.stream().map(p -> ProductResponse.summary(p,
                bySku.getOrDefault(p.getSku(), List.of()))).toList();
        }
        branches.require(branchCode);
        Map<Long, BranchInventory> offers = inventory.findForBranch(branchCode).stream()
                .collect(Collectors.toMap(i -> i.getProduct().getId(), Function.identity()));
        return found.stream().map(p -> ProductResponse.from(p, offers.get(p.getId()))).toList();
    }
    public ProductResponse byId(Long id, String branchCode) {
        Product p = repository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (branchCode == null || branchCode.isBlank()) return ProductResponse.summary(p, inventory.findForProduct(id));
        return ProductResponse.from(p, offer(p.getSku(), branchCode));
    }
    public List<AvailabilityResponse> offers(List<String> skus) {
        if (skus == null || skus.isEmpty() || skus.size() > 100 ||
            skus.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_-]{1,50}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de SKU inválida");
        List<String> unique = skus.stream().distinct().toList();
        return inventory.findForSkus(unique).stream().map(AvailabilityResponse::from).toList();
    }
    public List<String> categories() { return repository.findActiveCategories(); }
    public List<AvailabilityResponse> comparison(Long id) {
        repository.findByIdAndActiveTrue(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return inventory.findForProduct(id).stream().map(AvailabilityResponse::from).toList();
    }
    public AvailabilityResponse availability(String sku, String branchCode) { return AvailabilityResponse.from(offer(sku, branchCode)); }
    private BranchInventory offer(String sku, String branchCode) {
        branches.require(branchCode);
        Product product = repository.findBySku(sku).filter(Product::isActive).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return inventory.findByBranchCodeAndProductSku(branchCode, product.getSku()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no disponible en la sucursal"));
    }
    private static String normalize(String value, int max, String name) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parámetro " + name + " demasiado largo");
        return v;
    }
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
