package ca.carletonu.controllers;

import ca.carletonu.models.Cart;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Transaction;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Shows what an order bought.
 *
 * Read only: the checkout itself is a command, dispatched from CartController.
 * This page reads the order's Transactions, which are saved, rather than a
 * flash message, so it still shows the order after a refresh.
 */
@Controller
public class CheckoutController {

    private final CurrentCustomer currentCustomer;
    private final CartRepository cartRepository;
    private final TransactionRepository transactionRepository;

    public CheckoutController(CurrentCustomer currentCustomer,
                              CartRepository cartRepository,
                              TransactionRepository transactionRepository) {
        this.currentCustomer = currentCustomer;
        this.cartRepository = cartRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Only the shopper's own checked-out carts are shown. Anything else is a
     * 404, the same as an id that does not exist, so the page does not reveal
     * which order ids belong to someone else.
     */
    @GetMapping("/checkout/confirmation/{cartId}")
    public String confirmation(@PathVariable Long cartId, Model model) {
        Long customerId = currentCustomer.find().map(Customer::getId).orElse(null);

        Cart cart = cartRepository.findById(cartId)
                .filter(found -> !found.isOpen())
                .filter(found -> found.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order " + cartId));

        List<Transaction> purchases = transactionRepository.findByCartIdOrderByIdAsc(cart.getId());

        model.addAttribute("orderId", cart.getId());
        model.addAttribute("purchases", purchases);
        model.addAttribute("itemCount", purchases.stream()
                .mapToInt(purchase -> purchase.getQuantity() == null ? 0 : purchase.getQuantity())
                .sum());
        model.addAttribute("total", purchases.stream().mapToDouble(Transaction::getSubtotal).sum());
        return "checkout/confirmation";
    }
}
