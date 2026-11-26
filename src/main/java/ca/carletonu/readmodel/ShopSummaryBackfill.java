package ca.carletonu.readmodel;

import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ProductKind;
import ca.carletonu.events.ShopCreatedEvent;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts shops that predate the read model into it.
 *
 * The catalogue is filled by events, but nothing replays the ones that were
 * never published: the seeded demo shops go straight to ShopRepository, and on
 * Azure there is no broker at all, so no event is ever published. Without this
 * the browse and search pages would be empty on the deployed site even though
 * the shops exist.
 *
 * It runs on ApplicationReadyEvent rather than as an ApplicationRunner, which
 * is what puts it after DemoDataSeeder: Spring Boot runs every
 * ApplicationRunner first and publishes this event afterwards. Seeding and then
 * finding nothing to backfill would be a quiet, annoying bug.
 *
 * It replays through ShopSummaryProjection rather than writing summaries
 * itself, so a backfilled shop and an event driven one go through exactly the
 * same code. The projection already skips what it has, so running this on
 * every restart is safe and writes nothing the second time.
 */
@Component
public class ShopSummaryBackfill {

    private static final Logger log = LoggerFactory.getLogger(ShopSummaryBackfill.class);

    private final ShopRepository shopRepository;
    private final ShopSummaryRepository shopSummaryRepository;
    private final ShopSummaryProjection projection;

    public ShopSummaryBackfill(ShopRepository shopRepository,
                               ShopSummaryRepository shopSummaryRepository,
                               ShopSummaryProjection projection) {
        this.shopRepository = shopRepository;
        this.shopSummaryRepository = shopSummaryRepository;
        this.projection = projection;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfill() {
        int shopsAdded = 0;

        for (Shop shop : shopRepository.findAll()) {
            if (alreadyDescribed(shop)) {
                continue;
            }
            projection.apply(new ShopCreatedEvent(
                    shop.getId(),
                    shop.getSeller() == null ? null : shop.getSeller().getId(),
                    shop.getName(),
                    shop.getDescription(),
                    shop.getType()
            ));
            shopsAdded++;

            for (Product product : shop.getProducts()) {
                projection.apply(productAdded(shop, product));
            }
        }

        if (shopsAdded > 0) {
            log.info("Catalogue backfilled with {} shop(s) that had no event", shopsAdded);
        }
    }

    /**
     * A summary can exist without being described, when a product event
     * arrived before its shop's. Those still need backfilling.
     */
    private boolean alreadyDescribed(Shop shop) {
        return shopSummaryRepository.findByShopId(shop.getId())
                .map(ShopSummary::isDescribed)
                .orElse(false);
    }

    private static ProductAddedEvent productAdded(Shop shop, Product product) {
        ProductKind kind = product instanceof Item ? ProductKind.ITEM : ProductKind.SERVICE;
        return new ProductAddedEvent(
                shop.getId(),
                product.getId(),
                kind,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                null,
                null,
                null
        );
    }
}
