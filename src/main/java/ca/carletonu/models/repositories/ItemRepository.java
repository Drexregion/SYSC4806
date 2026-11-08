package ca.carletonu.models.repositories;

import ca.carletonu.models.Item;
import org.springframework.data.repository.CrudRepository;

public interface ItemRepository extends CrudRepository<Item, Long> {

}
