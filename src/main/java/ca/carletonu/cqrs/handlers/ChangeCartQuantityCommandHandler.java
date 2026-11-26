package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.ChangeCartQuantityCommand;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.CartStatus;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.repositories.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeCartQuantityCommandHandler {

    private final CartRepository cartRepository;

    public ChangeCartQuantityCommandHandler(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public void handle(ChangeCartQuantityCommand command) {
        // First, as the controller used to check it, so a shopper with no
        // account is told that rather than about the quantity.
        if (command.customerId() == null) {
            throw new IllegalArgumentException(RemoveFromCartCommandHandler.NO_CUSTOMER);
        }
        if (command.quantity() == null || command.quantity() <= 0) {
            throw new IllegalArgumentException("Choose a quantity of at least one.");
        }

        Cart cart = cartRepository.findByCustomerIdAndStatus(command.customerId(), CartStatus.OPEN)
                .orElseThrow(() -> new IllegalArgumentException("That product is not in your cart."));
        Product product = cart.getItems().stream()
                .map(CartItem::getProduct)
                .filter(candidate -> candidate.getId().equals(command.productId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That product is not in your cart."));

        if (product instanceof Item item) {
            checkStock(item, command.quantity());
        }

        cart.setQuantity(product, command.quantity());
        cartRepository.save(cart);
    }

    private void checkStock(Item item, int wanted) {
        if (wanted <= item.getQuantity()) {
            return;
        }
        if (item.getQuantity() <= 0) {
            throw new IllegalArgumentException(item.getName() + " is out of stock.");
        }
        throw new IllegalArgumentException("Only " + item.getQuantity() + " of " + item.getName() + " in stock.");
    }
}
