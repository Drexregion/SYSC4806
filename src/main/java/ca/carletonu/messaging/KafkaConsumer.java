package ca.carletonu.messaging;

import ca.carletonu.events.ShopCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads shop-created events off the log.
 *
 * For now it only logs what it receives. In Milestone 2 this is where the
 * read models for search and browsing get updated, which is the point of
 * splitting the write side from the read side in the first place.
 *
 * Only exists under the "kafka" profile, so no listener container starts
 * when there is no broker to listen to.
 */
@Component
@Profile("kafka")
public class KafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    @KafkaListener(topics = "shop-created", groupId = "mini-shopify-group")
    public void consumeShopCreated(ShopCreatedEvent shopCreatedEvent) {
        log.info("ShopCreatedEvent received: shopId={} name={} sellerId={}",
                shopCreatedEvent.shopId(),
                shopCreatedEvent.name(),
                shopCreatedEvent.sellerId());
    }
}
