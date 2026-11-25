package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the command to check out a customer's cart.
 *
 * The write side for checkout, the same shape as CreateShopCommandHandler and
 * AddToCartCommandHandler: the rules live here, and the controller only turns
 * a refusal into a message.
 *
 * Checking out closes the cart and takes what it held out of stock. Payment
 * is out of scope; this is the step that makes the purchase real in the
 * write model.
 */
@Service
public class CheckoutCommandHandler {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;

    public CheckoutCommandHandler(CustomerRepository customerRepository, CartRepository cartRepository) {
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
    }

    /**
     * Checks the customer's open cart can be bought, then reduces the stock of
     * every item in it and closes the cart.
     *
     * Every check runs before anything changes, so a refused checkout leaves
     * the cart open and the stock untouched.
     *
     * @return what was bought
     * @throws IllegalArgumentException if the checkout is refused; the message says why
     */
    @Transactional
    public CheckoutResult handle(CheckoutCommand command) {
        if (command.customerId() == null) {
            throw new IllegalArgumentException("There is no customer account to check out.");
        }

        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new IllegalArgumentException("There is no customer account to check out."));

        // findOpenCart, never openCartFor: checking out must not create a cart
        Cart cart = cartRepository.findOpenCart(customer)
                .filter(open -> !open.getItems().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException("Your cart is empty."));

        checkStock(cart);

        // Stock is checked on the write model inside this transaction, the
        // same reason AddToCartCommandHandler does: a read model could lag.
        for (CartItem line : cart.getItems()) {
            if (line.getProduct() instanceof Item item) {
                item.setQuantity(item.getQuantity() - line.getQuantity());
            }
        }

        CheckoutResult result = new CheckoutResult(cart.getId(), cart.getItemCount(), cart.getTotal());

        cart.checkOut();
        cartRepository.save(cart);

        return result;
    }

    /**
     * Refuses the checkout when any item in the cart has less stock than the
     * cart holds. Stock can have dropped since the item was added, because
     * adding to a cart does not reserve it. Every short item is named at once,
     * so the shopper can fix the cart in one go.
     */
    private void checkStock(Cart cart) {
        List<String> shortages = new ArrayList<>();

        for (CartItem line : cart.getItems()) {
            if (line.getProduct() instanceof Item item && line.getQuantity() > item.getQuantity()) {
                shortages.add(item.getQuantity() <= 0
                        ? item.getName() + " is out of stock"
                        : "only " + item.getQuantity() + " of " + item.getName() + " left");
            }
        }

        if (!shortages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Your cart has more than is in stock: " + String.join(", ", shortages) + ".");
        }
    }
}
