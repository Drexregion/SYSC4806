package ca.carletonu.models.repositories;

import ca.carletonu.models.Cart;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Customer;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface CartRepository extends CrudRepository<Cart, Long> {

    Optional<Cart> findByCustomerIdAndStatus(Long customerId, CartStatus status);

    List<Cart> findByCustomerId(Long customerId);

    default Optional<Cart> findOpenCart(Customer customer) {
        return findByCustomerIdAndStatus(customer.getId(), CartStatus.OPEN);
    }

    /**
     * The customer's open cart, made and saved if they do not have one yet.
     * The way to get a cart for a customer, so callers never create a second.
     */
    default Cart openCartFor(Customer customer) {
        return findOpenCart(customer).orElseGet(() -> save(new Cart(customer)));
    }
}
