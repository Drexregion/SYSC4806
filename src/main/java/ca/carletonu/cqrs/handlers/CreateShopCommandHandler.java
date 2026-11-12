package ca.carletonu.cqrs.handlers;

import ca.carletonu.cqrs.commands.CreateShopCommand;
import ca.carletonu.events.ShopCreatedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateShopCommandHandler {

    private final ShopRepository shopRepository;
    private final SellerRepository sellerRepository;
    private final KafkaProducer kafkaProducer;

    public CreateShopCommandHandler(
            ShopRepository shopRepository,
            SellerRepository sellerRepository,
            KafkaProducer kafkaProducer) {

        this.shopRepository = shopRepository;
        this.sellerRepository = sellerRepository;
        this.kafkaProducer = kafkaProducer;
    }

    @Transactional
    public Shop handle(CreateShopCommand command) {

        // Retrieve the seller.
        Seller seller = sellerRepository.findById(command.sellerId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Seller not found"));

        // Create the shop.
        Shop shop = new Shop(
                command.name(),
                command.description(),
                command.type(),
                seller
        );

        // Save the shop.
        Shop savedShop = shopRepository.save(shop);

        // Create an event describing the successful creation.
        ShopCreatedEvent event = new ShopCreatedEvent(
                savedShop.getId(),
                seller.getId(),
                savedShop.getName(),
                savedShop.getDescription(),
                savedShop.getType()
        );

        // Publish the event to Kafka.
        kafkaProducer.sendShopCreatedEvent(event);

        return savedShop;
    }
}