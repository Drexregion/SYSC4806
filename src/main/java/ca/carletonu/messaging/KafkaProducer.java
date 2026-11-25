package ca.carletonu.messaging;

import ca.carletonu.events.ShopCreatedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes domain events to Kafka.
 *
 * Only exists under the "kafka" profile. Azure App Service has no broker, so
 * the deployed application runs without this bean and the command handler
 * simply skips publishing. Start the app with
 * --spring.profiles.active=kafka (and the broker from docker-compose.yml)
 * to get the event flow.
 */
@Component
@Profile("kafka")
public class KafkaProducer {

    private static final String SHOP_CREATED_TOPIC = "shop-created";

    private final KafkaTemplate<String, ShopCreatedEvent> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, ShopCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendShopCreatedEvent(ShopCreatedEvent shopCreatedEvent) {
        this.kafkaTemplate.send(SHOP_CREATED_TOPIC,
                shopCreatedEvent.shopId().toString(),
                shopCreatedEvent);
    }
}
