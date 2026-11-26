package ca.carletonu.readmodel;

import ca.carletonu.events.ProductKind;
import ca.carletonu.models.Item;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backfilling the catalogue for shops that never published an event.
 *
 * This is what keeps browse and search working with no broker, which is how
 * the app runs on Azure, and for the demo data, which goes straight to the
 * repository. Without it both pages would be empty on the deployed site.
 *
 * The shops here use names nothing else uses, so rows left in the shared
 * in-memory database by other tests cannot be mistaken for them.
 */
@SpringBootTest
class ShopSummaryBackfillTests {

    private static final String MARKER = "Quillfeather Bindery";

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ShopSummaryRepository shopSummaryRepository;

    @Autowired
    private ShopSummaryBackfill backfill;

    @AfterEach
    void removeWhatThisTestMade() {
        shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(MARKER, MARKER)
                .forEach(shop -> {
                    shopSummaryRepository.findByShopId(shop.getId())
                            .ifPresent(shopSummaryRepository::delete);
                    shopRepository.delete(shop);
                });
    }

    @Test
    void aShopWithNoEventBehindItStillReachesTheCatalogue() {
        Shop shop = shopRepository.save(new Shop(MARKER, "Hand bound books", "Bookbinding", null));

        backfill.backfill();

        ShopSummary summary = shopSummaryRepository.findByShopId(shop.getId()).orElseThrow();
        assertThat(summary.getName()).isEqualTo(MARKER);
        assertThat(summary.getType()).isEqualTo("Bookbinding");
        assertThat(summary.getDescription()).isEqualTo("Hand bound books");
        assertThat(summary.isDescribed()).isTrue();
    }

    @Test
    void runningItAgainChangesNothing() {
        Shop shop = shopRepository.save(new Shop(MARKER, "Hand bound books", "Bookbinding", null));

        backfill.backfill();
        long afterFirst = shopSummaryRepository.count();
        backfill.backfill();

        assertThat(shopSummaryRepository.count()).isEqualTo(afterFirst);
        assertThat(shopSummaryRepository.findByShopId(shop.getId())).isPresent();
    }

    @Test
    void itCountsTheShopsItemsAndServicesSeparately() {
        Shop shop = new Shop(MARKER, "Hand bound books", "Bookbinding", null);
        shop.addProduct(new Item("Ledger", "Linen cover", 40.0, 3, "Book"));
        shop.addProduct(new Item("Journal", "Pocket size", 18.0, 7, "Book"));
        shop.addProduct(new Service("Rebinding", "Repair a spine", 55.0, 90));
        shop = shopRepository.save(shop);

        backfill.backfill();

        ShopSummary summary = shopSummaryRepository.findByShopId(shop.getId()).orElseThrow();
        assertThat(summary.getProductCount()).isEqualTo(3);
        assertThat(summary.getItemCount()).isEqualTo(2);
        assertThat(summary.getServiceCount()).isEqualTo(1);
    }

    /**
     * A product event can arrive before its shop's, which leaves a summary
     * holding products and no name. The backfill has to finish those rather
     * than skip them, or the shop never becomes browsable.
     */
    @Test
    void itFinishesASummaryLeftHalfDoneByAnEarlyProductEvent() {
        Shop shop = shopRepository.save(new Shop(MARKER, "Hand bound books", "Bookbinding", null));

        ShopSummary placeholder = ShopSummary.placeholder(shop.getId());
        placeholder.addProduct(4242L, ProductKind.ITEM);
        shopSummaryRepository.save(placeholder);
        assertThat(shopSummaryRepository.findByShopId(shop.getId()).orElseThrow().isDescribed()).isFalse();

        backfill.backfill();

        ShopSummary summary = shopSummaryRepository.findByShopId(shop.getId()).orElseThrow();
        assertThat(summary.isDescribed()).isTrue();
        assertThat(summary.getName()).isEqualTo(MARKER);
        assertThat(summary.getProductCount()).isEqualTo(1);
    }
}
