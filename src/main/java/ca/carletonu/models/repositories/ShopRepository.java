package ca.carletonu.models.repositories;

import ca.carletonu.models.Shop;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends CrudRepository<Shop, Long> {

    /**
     * Shops whose name or category contains the given text, ignoring case.
     */
    List<Shop> findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(String name, String type);

    /**
     * The shop that sells the given product. Product has no reference back to
     * its shop, so the lookup goes through Shop's product list instead.
     */
    Optional<Shop> findFirstByProducts_Id(Long productId);
}
