package ca.carletonu.models.repositories;

import ca.carletonu.models.Shop;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ShopRepository extends CrudRepository<Shop, Long> {

    /**
     * Shops whose name or category contains the given text, ignoring case.
     */
    List<Shop> findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(String name, String type);
}
