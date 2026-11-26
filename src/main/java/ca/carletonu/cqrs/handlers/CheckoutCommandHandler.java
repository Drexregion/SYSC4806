package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CheckoutCommand;
import ca.carletonu.events.CheckoutCompletedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.models.repositories.TransactionRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles the command to check out a customer's cart.
 *
 * The write side for checkout, the same shape as CreateShopCommandHandler and
 * AddToCartCommandHandler: the rules live here, and the controller only turns
 * a refusal into a message.
 *
 * Checking out records a Transaction for every line, takes what the cart held
 * out of stock, closes the cart, and publishes a CheckoutCompletedEvent when a
 * broker is configured. Payment is out of scope; this is the step that makes
 * the purchase real in the write model.
 */
@Service
public class CheckoutCommandHandler {

    private final CustomerRepository customerRepository;
    private final CartRepository cartRepository;
    private final ShopRepository shopRepository;
    private final TransactionRepository transactionRepository;

    /**
     * The producer only exists under the "kafka" profile, so this is an
     * ObjectProvider rather than a hard dependency. Without a broker the
     * checkout still happens, the event just is not published.
     */
    private final ObjectProvider<KafkaProducer> kafkaProducer;

    public CheckoutCommandHandler(CustomerRepository customerRepository,
                                  CartRepository cartRepository,
                                  ShopRepository shopRepository,
                                  TransactionRepository transactionRepository,
                                  ObjectProvider<KafkaProducer> kafkaProducer) {
        this.customerRepository = customerRepository;
        this.cartRepository = cartRepository;
        this.shopRepository = shopRepository;
        this.transactionRepository = transactionRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Checks the customer's open cart can be bought, then records each line as
     * a Transaction, reduces the stock of every item in it, and closes the cart.
     *
     * Every check runs before anything changes, so a refused checkout writes no
     * Transactions and leaves the cart open and the stock untouched.
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

        Map<CartItem, Shop> shops = checkLines(cart);

        // Stock is checked on the write model inside this transaction, the
        // same reason AddToCartCommandHandler does: a read model could lag.
        List<CheckoutCompletedEvent.Line> eventLines = new ArrayList<>();
        for (CartItem line : cart.getItems()) {
            Product product = line.getProduct();
            Shop shop = shops.get(line);

            if (product instanceof Item item) {
                item.setQuantity(item.getQuantity() - line.getQuantity());
            }

            // The price is taken now, so the record keeps what was paid even if
            // the product's price changes later.
            transactionRepository.save(new Transaction(
                    cart.getId(), shop, product, customer, line.getQuantity(), product.getPrice()));

            eventLines.add(new CheckoutCompletedEvent.Line(
                    product.getId(), product.getName(), shop.getId(), line.getQuantity(), product.getPrice()));
        }

        CheckoutResult result = new CheckoutResult(cart.getId(), cart.getItemCount(), cart.getTotal());

        cart.checkOut();
        cartRepository.save(cart);

        CheckoutCompletedEvent event = new CheckoutCompletedEvent(
                cart.getId(), customer.getId(), eventLines, result.total());
        kafkaProducer.ifAvailable(producer -> producer.sendCheckoutCompletedEvent(event));

        return result;
    }

    /**
     * Refuses the checkout when any line cannot be bought: an item with less
     * stock than the cart holds, or a product no shop sells any more. Stock can
     * have dropped since the item was added, because adding to a cart does not
     * reserve it. Every problem is named at once, so the shopper can fix the
     * cart in one go.
     *
     * @return the shop that sells each line, found once here so the write uses
     *         exactly what was checked
     */
    private Map<CartItem, Shop> checkLines(Cart cart) {
        List<String> problems = new ArrayList<>();
        Map<CartItem, Shop> shops = new LinkedHashMap<>();

        for (CartItem line : cart.getItems()) {
            Product product = line.getProduct();

            if (product instanceof Item item && line.getQuantity() > item.getQuantity()) {
                problems.add(item.getQuantity() <= 0
                        ? item.getName() + " is out of stock"
                        : "only " + item.getQuantity() + " of " + item.getName() + " left");
            }

            Optional<Shop> shop = shopRepository.findFirstByProducts_Id(product.getId());
            if (shop.isPresent()) {
                shops.put(line, shop.get());
            } else {
                problems.add(product.getName() + " is no longer sold by any shop");
            }
        }

        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Checkout was refused: " + String.join(", ", problems) + ".");
        }
        return shops;
    }
}
