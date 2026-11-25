package ca.carletonu.readmodel;

import ca.carletonu.events.ShopCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the catalogue read model from shop-created events.
 *
 * Deliberately knows nothing about Kafka. The transport lives in
 * ShopCreatedListener, which only exists under the "kafka" profile; this class
 * is always available, so the projection can be driven directly in a test with
 * no broker running.
 *
 * Applying is idempotent: replaying the log from offset 0 rebuilds the same
 * table rather than duplicating rows, which is what makes the read model
 * disposable.
 */
@Component
public class ShopSummaryProjection {

    private static final Logger log = LoggerFactory.getLogger(ShopSummaryProjection.class);

    private final ShopSummaryRepository shopSummaryRepository;

    public ShopSummaryProjection(ShopSummaryRepository shopSummaryRepository) {
        this.shopSummaryRepository = shopSummaryRepository;
    }

    @Transactional
    public void apply(ShopCreatedEvent event) {
        if (shopSummaryRepository.findByShopId(event.shopId()).isPresent()) {
            log.info("Catalogue already holds shopId={}, skipping", event.shopId());
            return;
        }

        shopSummaryRepository.save(new ShopSummary(
                event.shopId(),
                event.sellerId(),
                event.name(),
                event.description(),
                event.type()
        ));

        log.info("Catalogue updated from event: shopId={} name={}", event.shopId(), event.name());
    }
}
