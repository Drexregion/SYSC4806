package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CreateShopCommand;
import ca.carletonu.events.ShopCreatedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles the command to create a shop.
 *
 * This is the write side: validation and persistence live here rather than in
 * the controller, so the rules are in one findable place.
 */
@Service
public class CreateShopCommandHandler {

    private final ShopRepository shopRepository;
    private final SellerRepository sellerRepository;

    /**
     * The producer only exists under the "kafka" profile, so this is an
     * ObjectProvider rather than a hard dependency. Without a broker the shop
     * is still created, the event just is not published.
     */
    private final ObjectProvider<KafkaProducer> kafkaProducer;

    public CreateShopCommandHandler(
            ShopRepository shopRepository,
            SellerRepository sellerRepository,
            ObjectProvider<KafkaProducer> kafkaProducer) {

        this.shopRepository = shopRepository;
        this.sellerRepository = sellerRepository;
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Validates the command, saves the shop, and publishes a ShopCreatedEvent
     * when a broker is configured.
     *
     * @throws IllegalArgumentException if the command is not valid, or names a
     *                                  seller that does not exist
     */
    @Transactional
    public Shop handle(CreateShopCommand command) {

        String error = validate(command);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }

        Seller seller = sellerRepository.findById(command.sellerId())
                .orElseThrow(() -> new IllegalArgumentException("Seller not found"));

        Shop shop = new Shop(
                command.name().trim(),
                command.description() == null ? null : command.description().trim(),
                command.type() == null ? null : command.type().trim(),
                seller
        );

        Shop savedShop = shopRepository.save(shop);

        ShopCreatedEvent event = new ShopCreatedEvent(
                savedShop.getId(),
                seller.getId(),
                savedShop.getName(),
                savedShop.getDescription(),
                savedShop.getType()
        );

        kafkaProducer.ifAvailable(producer -> producer.sendShopCreatedEvent(event));

        return savedShop;
    }

    /**
     * The rules a shop has to satisfy. Returns the message to show the
     * merchant, or null when the command is fine.
     *
     * The form marks these fields required, but that is browser side only and
     * a direct POST goes straight past it, so the check belongs here too.
     */
    private String validate(CreateShopCommand command) {
        if (command.sellerId() == null) {
            return "A shop needs a seller.";
        }
        if (command.name() == null || command.name().isBlank()) {
            return "Shop name is required.";
        }
        if (command.name().trim().length() > 120) {
            return "Shop name has to be 120 characters or fewer.";
        }
        if (command.type() == null || command.type().isBlank()) {
            return "Category is required.";
        }
        return null;
    }
}
