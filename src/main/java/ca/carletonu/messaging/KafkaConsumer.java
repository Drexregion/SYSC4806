package ca.carletonu.messaging;

import ca.carletonu.events.ShopCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {
    @KafkaListener(
            topics = "shop-created",
            groupId = "mini-shopify-group"
    )
    public void consumeShopCreated(ShopCreatedEvent shopCreatedEvent) {
        System.out.println("Received ShopCreatedEvent!");

        System.out.println("Shop ID: " + shopCreatedEvent.shopId());
        System.out.println("Shop Name: " + shopCreatedEvent.name());
        System.out.println("Seller ID: " + shopCreatedEvent.sellerId());
    }
}
