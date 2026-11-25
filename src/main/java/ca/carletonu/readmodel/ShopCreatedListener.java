package ca.carletonu.readmodel;

import ca.carletonu.events.ShopCreatedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the catalogue projection from Kafka.
 *
 * Its own group id, separate from the logging consumer, so both receive every
 * event independently. That fan-out is what lets one log serve any number of
 * read models.
 *
 * Only exists under the "kafka" profile. Without a broker the projection class
 * is still there, it simply never gets fed.
 */
@Component
@Profile("kafka")
public class ShopCreatedListener {

    private final ShopSummaryProjection projection;

    public ShopCreatedListener(ShopSummaryProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = "shop-created", groupId = "catalogue-projection")
    public void on(ShopCreatedEvent event) {
        projection.apply(event);
    }
}
