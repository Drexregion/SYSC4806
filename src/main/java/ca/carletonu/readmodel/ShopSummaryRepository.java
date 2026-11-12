package ca.carletonu.readmodel;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

/**
 * The query side's repository. Only the catalogue reads from this, and only
 * the projection writes to it.
 */
@RepositoryRestResource(path = "shop-summaries")
public interface ShopSummaryRepository extends CrudRepository<ShopSummary, Long> {

    Optional<ShopSummary> findByShopId(Long shopId);

    List<ShopSummary> findByNameContainingIgnoreCaseOrTypeContainingIgnoreCaseOrderByNameAsc(
            String name, String type);

    List<ShopSummary> findAllByOrderByNameAsc();
}
