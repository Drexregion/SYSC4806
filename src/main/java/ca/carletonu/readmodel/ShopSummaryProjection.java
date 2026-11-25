package ca.carletonu.readmodel;

import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ShopCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the catalogue read model from shop-created and product-added events.
 *
 * Deliberately knows nothing about Kafka. The transport lives in the
 * listeners, which only exist under the "kafka" profile; this class is always
 * available, so the projection can be driven directly in a test with no broker
 * running.
 *
 * Applying is idempotent: replaying the log from offset 0 rebuilds the same
 * table rather than duplicating rows or double counting products, which is
 * what makes the read model disposable.
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
        ShopSummary existing = shopSummaryRepository.findByShopId(event.shopId()).orElse(null);

        if (existing != null && existing.isDescribed()) {
            log.info("Catalogue already holds shopId={}, skipping", event.shopId());
            return;
        }

        if (existing != null) {
            // Its products got here first, so fill in the shop around them.
            existing.describe(event.sellerId(), event.name(), event.description(), event.type());
            shopSummaryRepository.save(existing);
        } else {
            shopSummaryRepository.save(new ShopSummary(
                    event.shopId(),
                    event.sellerId(),
                    event.name(),
                    event.description(),
                    event.type()
            ));
        }

        log.info("Catalogue updated from event: shopId={} name={}", event.shopId(), event.name());
    }

    @Transactional
    public void apply(ProductAddedEvent event) {
        ShopSummary summary = shopSummaryRepository.findByShopId(event.shopId())
                .orElseGet(() -> ShopSummary.placeholder(event.shopId()));

        if (!summary.addProduct(event.productId(), event.kind())) {
            log.info("Catalogue already counts productId={} for shopId={}, skipping",
                    event.productId(), event.shopId());
            return;
        }

        shopSummaryRepository.save(summary);

        log.info("Catalogue updated from event: shopId={} productId={} kind={} items={} services={}",
                event.shopId(), event.productId(), event.kind(),
                summary.getItemCount(), summary.getServiceCount());
    }
}
