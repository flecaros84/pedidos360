package cl.duoc.pedidos360.products.service;
import cl.duoc.pedidos360.products.api.BranchResponse;
import cl.duoc.pedidos360.products.domain.Branch;
import cl.duoc.pedidos360.products.repository.BranchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
@Service @Transactional(readOnly = true)
public class BranchesService {
    private final BranchRepository repository;
    public BranchesService(BranchRepository repository) { this.repository = repository; }
    public List<BranchResponse> list() { return repository.findByActiveTrueOrderByNameAsc().stream().map(BranchResponse::from).toList(); }
    public Branch require(String code) {
        if (code == null || code.length() > 32 || code.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sucursal obligatoria");
        Branch branch = repository.findById(code).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Sucursal no encontrada"));
        if (!branch.isActive()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sucursal inactiva");
        return branch;
    }
}
