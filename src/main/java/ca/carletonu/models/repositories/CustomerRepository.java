package ca.carletonu.models.repositories;

import ca.carletonu.models.Customer;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface CustomerRepository extends CrudRepository<Customer, Long> {

    /** The oldest customer. Used as the stand-in shopper until there is sign-in. */
    Optional<Customer> findFirstByOrderByIdAsc();
}
