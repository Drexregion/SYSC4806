package ca.carletonu.readmodel;

import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ShopCreatedEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the catalogue projection when there is no broker.
 *
 * ShopCreatedListener and ProductAddedListener only exist under the "kafka"
 * profile. Without that profile nothing consumed the events at all, so a shop
 * created after startup was written to the write model and never reached the
 * read model: browse and search could not see it until the next restart. That
 * is how the app runs on Azure, and locally by default, so this closes it.
 *
 * Profiled "!kafka", the exact complement of the two Kafka listeners, so only
 * one of the two paths is ever live and an event is applied once. The
 * projection is idempotent either way.
 */
@Component
@Profile("!kafka")
public class InProcessProjectionListener {

    private final ShopSummaryProjection projection;

    public InProcessProjectionListener(ShopSummaryProjection projection) {
        this.projection = projection;
    }

    @EventListener
    public void on(ShopCreatedEvent event) {
        projection.apply(event);
    }

    @EventListener
    public void on(ProductAddedEvent event) {
        projection.apply(event);
    }
}
