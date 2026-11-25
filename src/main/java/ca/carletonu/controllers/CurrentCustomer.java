package ca.carletonu.controllers;

import ca.carletonu.models.Customer;
import ca.carletonu.models.repositories.CustomerRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Which customer the shopper is.
 *
 * There is no sign-in yet, so this stands in the first customer in the
 * database, the same way the landing page stands in the first seller. The
 * demo data always writes one. When sign-in arrives, this is the one place
 * that changes: it returns the signed-in customer instead, and nothing that
 * asks it needs to know.
 */
@Component
public class CurrentCustomer {

    private final CustomerRepository customerRepository;

    public CurrentCustomer(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * The shopper, or empty when there is no customer to stand in.
     */
    public Optional<Customer> find() {
        return customerRepository.findFirstByOrderByIdAsc();
    }
}
