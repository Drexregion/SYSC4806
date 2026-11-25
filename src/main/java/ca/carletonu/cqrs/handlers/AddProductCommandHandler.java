package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.AddProductCommand;
import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ProductKind;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ProductRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the command to add a product, an item or a service, to a shop.
 *
 * The write side for products: validation and persistence live here rather
 * than in the controller, the same way CreateShopCommandHandler does it for
 * shops.
 */
@org.springframework.stereotype.Service
public class AddProductCommandHandler {

    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;

    /**
     * The producer only exists under the "kafka" profile, so this is an
     * ObjectProvider rather than a hard dependency. Without a broker the
     * product is still saved, the event just is not published.
     */
    private final ObjectProvider<KafkaProducer> kafkaProducer;

    public AddProductCommandHandler(ShopRepository shopRepository,
                                    ProductRepository productRepository,
                                    ObjectProvider<KafkaProducer> kafkaProducer) {
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Validates the command, saves an Item or a Service against its shop, and
     * publishes a ProductAddedEvent of the matching kind when a broker is
     * configured.
     *
     * @return the saved Item or Service
     * @throws ShopNotFoundException    if the command names a shop that does not exist
     * @throws IllegalArgumentException if the product is not valid
     */
    @Transactional
    public Product handle(AddProductCommand command) {

        if (command.shopId() == null) {
            throw new IllegalArgumentException("A product needs a shop.");
        }

        Shop shop = shopRepository.findById(command.shopId())
                .orElseThrow(() -> new ShopNotFoundException(command.shopId()));

        String error = validate(command);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        // Saved through its own repository first so the id is assigned now.
        // Saving only the shop would merge, and merge cascades onto a copy of
        // the product, leaving this instance without an id for the event.
        Product saved = productRepository.save(build(command));
        shop.addProduct(saved);

        ProductAddedEvent event = eventFor(shop.getId(), saved);
        kafkaProducer.ifAvailable(producer -> producer.sendProductAddedEvent(event));

        return saved;
    }

    private Product build(AddProductCommand command) {
        String name = command.name().trim();
        String description = blankToNull(command.description());

        return switch (command.kind()) {
            case ITEM -> new Item(name, description, command.price(),
                    command.quantity(), blankToNull(command.productType()));
            case SERVICE -> new Service(name, description, command.price(), command.duration());
        };
    }

    private ProductAddedEvent eventFor(Long shopId, Product saved) {
        if (saved instanceof Service service) {
            return ProductAddedEvent.service(shopId, service.getId(), service.getName(),
                    service.getDescription(), service.getPrice(), service.getDuration());
        }
        Item item = (Item) saved;
        return ProductAddedEvent.item(shopId, item.getId(), item.getName(),
                item.getDescription(), item.getPrice(), item.getQuantity(), item.getProductType());
    }

    /**
     * The rules a product has to satisfy. Returns the message to show the
     * merchant, or null when the command is fine.
     *
     * The form marks these fields required, but that is browser side only and
     * a direct POST goes straight past it, so the check belongs here too.
     */
    private String validate(AddProductCommand command) {
        if (command.kind() == null) {
            return "Choose whether this is an item or a service.";
        }
        if (command.name() == null || command.name().isBlank()) {
            return "Name is required.";
        }
        if (command.name().trim().length() > 120) {
            return "Name has to be 120 characters or fewer.";
        }
        if (command.price() == null || command.price().isNaN() || command.price() < 0) {
            return "Price must be zero or more.";
        }
        if (command.kind() == ProductKind.ITEM) {
            if (command.quantity() == null) {
                return "Inventory is required for an item.";
            }
            if (command.quantity() < 0) {
                return "Inventory cannot be negative.";
            }
        }
        if (command.kind() == ProductKind.SERVICE) {
            if (command.duration() == null) {
                return "Duration is required for a service.";
            }
            if (command.duration() <= 0) {
                return "Duration must be at least one minute.";
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
