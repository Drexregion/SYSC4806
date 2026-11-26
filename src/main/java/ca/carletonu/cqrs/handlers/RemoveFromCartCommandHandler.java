package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.RemoveFromCartCommand;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Product;
import ca.carletonu.models.repositories.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemoveFromCartCommandHandler {

    /** Shared with ChangeCartQuantityCommandHandler: both change the cart on the cart page. */
    static final String NO_CUSTOMER = "There is no customer account with a cart yet.";

    private final CartRepository cartRepository;

    public RemoveFromCartCommandHandler(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public void handle(RemoveFromCartCommand command) {
        if (command.customerId() == null) {
            throw new IllegalArgumentException(NO_CUSTOMER);
        }

        Cart cart = cartRepository.findByCustomerIdAndStatus(command.customerId(), CartStatus.OPEN)
                .orElseThrow(() -> new IllegalArgumentException("That product is not in your cart."));
        Product product = cart.getItems().stream()
                .map(CartItem::getProduct)
                .filter(candidate -> candidate.getId().equals(command.productId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That product is not in your cart."));

        cart.removeProduct(product);
        cartRepository.save(cart);
    }
}
