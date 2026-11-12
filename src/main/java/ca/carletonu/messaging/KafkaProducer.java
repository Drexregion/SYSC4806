package ca.carletonu.messaging;

import ca.carletonu.events.ShopCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaProducer {
    private  static final String SHOP_CREATED_TOPIC ="shop-created";
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
