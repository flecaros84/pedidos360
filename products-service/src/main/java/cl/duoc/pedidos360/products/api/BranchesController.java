package cl.duoc.pedidos360.products.api;
import cl.duoc.pedidos360.products.service.BranchesService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/branches")
public class BranchesController {
    private final BranchesService service;
    public BranchesController(BranchesService service) { this.service = service; }
    @GetMapping public List<BranchResponse> list() { return service.list(); }
}
