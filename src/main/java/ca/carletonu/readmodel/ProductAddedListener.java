package ca.carletonu.readmodel;

import ca.carletonu.events.ProductAddedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds product-added events into the catalogue projection.
 *
 * Same group id as ShopCreatedListener: both feed the same read model, they
 * just read different topics.
 *
 * Only exists under the "kafka" profile.
 */
@Component
@Profile("kafka")
public class ProductAddedListener {

    private final ShopSummaryProjection projection;

    public ProductAddedListener(ShopSummaryProjection projection) {
        this.projection = projection;
    }

    @KafkaListener(topics = "product-added", groupId = "catalogue-projection")
    public void on(ProductAddedEvent event) {
        projection.apply(event);
    }
}
