package ca.carletonu.models.repositories;

import ca.carletonu.models.CartItem;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface CartItemRepository extends CrudRepository<CartItem, Long> {

    List<CartItem> findByCartId(Long cartId);
}
