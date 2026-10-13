package cl.duoc.pedidos360.products.api;
import cl.duoc.pedidos360.products.domain.Branch;
public record BranchResponse(String code, String name, String city) {
    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getCode(), branch.getName(), branch.getCity());
    }
}
