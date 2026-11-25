package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.AddToCartCommand;
import ca.carletonu.events.CartItemAddedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ProductRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the command to add a product to a customer's cart.
 *
 * The write side for carts: the stock rule lives here rather than in the
 * controller, the same way CreateShopCommandHandler holds the rules for shops.
 * Merging a repeat add into the existing line is the Cart's own rule, so this
 * only decides whether the add is allowed.
 *
 * The stock check reads the write model, not a read model, on purpose: a read
 * model can lag, and checking stock against a stale copy could let a shopper
 * add more than the shop has.
 */
@Service
public class AddToCartCommandHandler {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;

    /**
     * The producer only exists under the "kafka" profile, so this is an
     * ObjectProvider rather than a hard dependency. Without a broker the
     * product is still added, the event just is not published.
     */
    private final ObjectProvider<KafkaProducer> kafkaProducer;

    public AddToCartCommandHandler(CustomerRepository customerRepository,
                                   ProductRepository productRepository,
                                   CartRepository cartRepository,
                                   ObjectProvider<KafkaProducer> kafkaProducer) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.cartRepository = cartRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Checks the add, puts the product in the customer's open cart (opening
     * one if they have none), and publishes a CartItemAddedEvent when a broker
     * is configured.
     *
     * Every check runs before anything is saved, so a refused add writes
     * nothing at all, not even an empty cart.
     *
     * @return the cart line that now holds the product
     * @throws ProductNotFoundException if the product does not exist
     * @throws IllegalArgumentException if the add is refused; the message says why
     */
    @Transactional
    public CartItem handle(AddToCartCommand command) {
        if (command.quantity() == null || command.quantity() <= 0) {
            throw new IllegalArgumentException("Choose a quantity of at least one.");
        }
        if (command.customerId() == null) {
            throw new IllegalArgumentException("There is no customer account to add to.");
        }
        if (command.productId() == null) {
            throw new IllegalArgumentException("Choose a product to add.");
        }

        Customer customer = customerRepository.findById(command.customerId())
                .orElseThrow(() -> new IllegalArgumentException("There is no customer account to add to."));
        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new ProductNotFoundException(command.productId()));

        // A service is time booked with the merchant, not stock on a shelf,
        // so only an item has a limit to check.
        if (product instanceof Item item) {
            checkStock(customer, item, command.quantity());
        }

        // openCartFor has already saved a new cart, so it has its id here;
        // this save writes the new or changed line.
        Cart cart = cartRepository.openCartFor(customer);
        CartItem line = cart.addProduct(product, command.quantity());
        cartRepository.save(cart);

        CartItemAddedEvent event = new CartItemAddedEvent(
                cart.getId(),
                customer.getId(),
                product.getId(),
                command.quantity(),
                line.getQuantity()
        );
        kafkaProducer.ifAvailable(producer -> producer.sendCartItemAddedEvent(event));

        return line;
    }

    /**
     * Refuses the add when what the cart already holds plus what is being
     * added would be more than the shop has.
     */
    private void checkStock(Customer customer, Item item, int adding) {
        int inStock = item.getQuantity();
        int alreadyInCart = quantityInOpenCart(customer, item);

        if (alreadyInCart + adding <= inStock) {
            return;
        }
        if (inStock <= 0) {
            throw new IllegalArgumentException(item.getName() + " is out of stock.");
        }

        String message = "Only " + inStock + " of " + item.getName() + " in stock";
        if (alreadyInCart > 0) {
            message += ", and your cart already has " + alreadyInCart;
        }
        throw new IllegalArgumentException(message + ".");
    }

    /**
     * Read with findOpenCart, never openCartFor, so checking stock can never
     * write a cart.
     */
    private int quantityInOpenCart(Customer customer, Product product) {
        return cartRepository.findOpenCart(customer)
                .flatMap(cart -> cart.getItems().stream()
                        .filter(line -> sameProduct(line.getProduct(), product))
                        .findFirst())
                .map(CartItem::getQuantity)
                .orElse(0);
    }

    /** By id once saved, by identity before that; mirrors Cart's own matching. */
    private static boolean sameProduct(Product a, Product b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }
}
