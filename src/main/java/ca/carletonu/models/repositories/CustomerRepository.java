package ca.carletonu.models.repositories;

import ca.carletonu.models.Customer;
import org.springframework.data.repository.CrudRepository;

public interface CustomerRepository extends CrudRepository<Customer, Long> {

}
