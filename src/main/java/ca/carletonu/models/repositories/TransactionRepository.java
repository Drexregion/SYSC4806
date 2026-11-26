package ca.carletonu.models.repositories;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Transaction;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TransactionRepository extends CrudRepository<Transaction, Long> {

    /**
     * A customer's purchase history, newest first. Transaction is the single
     * record of a purchase, so this replaces the old Customer.transactionHistory strings.
     */
    List<Transaction> findByCustomerOrderByDateDesc(Customer customer);

    /** One checkout's rows, in the order they were written. */
    List<Transaction> findByCartIdOrderByIdAsc(Long cartId);
}
