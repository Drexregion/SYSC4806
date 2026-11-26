package ca.carletonu.readmodel;

import ca.carletonu.events.ProductAddedEvent;
import ca.carletonu.events.ShopCreatedEvent;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read model, driven by events rather than by the write model.
 *
 * The projection is applied directly instead of through a broker, so these run
 * in CI with no Kafka. What they prove is the behaviour that matters: the
 * catalogue is built from events, replay does not duplicate rows, and the page
 * shows nothing the log did not put there.
 */
@IntegrationTest
class ShopSummaryProjectionTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ShopSummaryRepository shopSummaryRepository;

    @Autowired
    private ShopSummaryProjection projection;

    @Test
    void anEventBecomesARowInTheReadModel() {
        projection.apply(event(1001L, "Ember & Oak", "Cast iron cookware", "Kitchen"));

        assertThat(shopSummaryRepository.findByShopId(1001L))
                .isPresent()
                .get()
                .satisfies(summary -> {
                    assertThat(summary.getName()).isEqualTo("Ember & Oak");
                    assertThat(summary.getType()).isEqualTo("Kitchen");
                    assertThat(summary.getDescription()).isEqualTo("Cast iron cookware");
                });
    }

    @Test
    void replayingTheSameEventDoesNotDuplicateTheRow() {
        projection.apply(event(1002L, "Tide & Timber", "Driftwood furniture", "Home"));
        projection.apply(event(1002L, "Tide & Timber", "Driftwood furniture", "Home"));
        projection.apply(event(1002L, "Tide & Timber", "Driftwood furniture", "Home"));

        assertThat(shopSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void aProductEventIsCountedOnItsShop() {
        projection.apply(event(1010L, "Ember & Oak", "Cast iron cookware", "Kitchen"));
        projection.apply(event(1011L, "Fernhill Plants", "Houseplants", "Garden"));

        projection.apply(productAdded(1010L, 1L, "Skillet"));
        projection.apply(productAdded(1010L, 2L, "Dutch oven"));

        assertThat(productCountOf(1010L)).isEqualTo(2);
        assertThat(productCountOf(1011L)).isZero();
    }

    @Test
    void replayingTheSameProductEventDoesNotDoubleCount() {
        projection.apply(event(1012L, "Tide & Timber", "Driftwood furniture", "Home"));

        projection.apply(productAdded(1012L, 3L, "Stool"));
        projection.apply(productAdded(1012L, 3L, "Stool"));
        projection.apply(productAdded(1012L, 3L, "Stool"));

        assertThat(productCountOf(1012L)).isEqualTo(1);
    }

    @Test
    void aProductThatArrivesBeforeItsShopIsKeptAndTheShopFilledInLater() {
        projection.apply(productAdded(1013L, 4L, "Card set"));

        // The shop has not been described yet, so the catalogue does not list it
        assertThat(shopSummaryRepository.findByNameIsNotNullOrderByNameAsc()).isEmpty();

        projection.apply(event(1013L, "Paper Lantern Press", "Letterpress cards", "Stationery"));

        assertThat(shopSummaryRepository.count()).isEqualTo(1);
        assertThat(shopSummaryRepository.findByShopId(1013L)).get().satisfies(summary -> {
            assertThat(summary.getName()).isEqualTo("Paper Lantern Press");
            assertThat(summary.getProductCount()).isEqualTo(1);
        });
    }

    @Test
    void itemsAndServicesAreCountedSeparately() {
        projection.apply(event(1015L, "Steeped", "Loose leaf tea", "Tea"));
        projection.apply(productAdded(1015L, 7L, "Sencha"));
        projection.apply(productAdded(1015L, 8L, "Earl Grey"));
        projection.apply(serviceAdded(1015L, 9L, "Guided Tasting"));

        assertThat(shopSummaryRepository.findByShopId(1015L)).get().satisfies(summary -> {
            assertThat(summary.getItemCount()).isEqualTo(2);
            assertThat(summary.getServiceCount()).isEqualTo(1);
            assertThat(summary.getProductCount()).isEqualTo(3);
        });
    }

    @Test
    void theCataloguePageShowsEachShopsItemsAndServices() {
        projection.apply(event(1014L, "Ember & Oak", "Cast iron cookware", "Kitchen"));
        projection.apply(productAdded(1014L, 5L, "Skillet"));
        projection.apply(productAdded(1014L, 6L, "Dutch oven"));
        projection.apply(serviceAdded(1014L, 10L, "Seasoning class"));

        assertThat(cataloguePage("")).contains("2 items · 1 service");
    }

    @Test
    void theCataloguePageShowsWhatTheLogPutThere() {
        projection.apply(event(1003L, "Paper Lantern Press", "Letterpress cards", "Stationery"));

        assertThat(cataloguePage(""))
                .contains("Paper Lantern Press")
                .contains("Letterpress cards")
                .contains("1 shop in the read model");
    }

    @Test
    void theCatalogueSearchesTheReadModelByNameAndCategory() {
        projection.apply(event(1004L, "Ember & Oak", "Cast iron", "Kitchen"));
        projection.apply(event(1005L, "Fernhill Plants", "Houseplants", "Garden"));

        assertThat(cataloguePage("kitchen"))
                .contains("Ember &amp; Oak")
                .doesNotContain("Fernhill Plants");

        assertThat(cataloguePage("fernhill"))
                .contains("Fernhill Plants")
                .doesNotContain("Ember &amp; Oak");
    }

    @Test
    void aShopInTheWriteModelIsInvisibleUntilAnEventArrives() {
        Shop shop = shopRepository.save(new Shop("Unseen Goods", "Not projected yet", "Home", null));

        // Nothing has been projected, so the catalogue is empty even though the
        // shop exists in the write model. That is the separation.
        assertThat(cataloguePage(""))
                .contains("The read model is empty")
                .doesNotContain("Unseen Goods");

        projection.apply(event(shop.getId(), shop.getName(), shop.getDescription(), shop.getType()));

        assertThat(cataloguePage("")).contains("Unseen Goods");
    }

    private ShopCreatedEvent event(Long shopId, String name, String description, String type) {
        return new ShopCreatedEvent(shopId, 1L, name, description, type);
    }

    private ProductAddedEvent productAdded(Long shopId, Long productId, String name) {
        return ProductAddedEvent.item(shopId, productId, name, null, 10.0, 1, null);
    }

    private ProductAddedEvent serviceAdded(Long shopId, Long productId, String name) {
        return ProductAddedEvent.service(shopId, productId, name, null, 30.0, 60);
    }

    private int productCountOf(Long shopId) {
        return shopSummaryRepository.findByShopId(shopId).orElseThrow().getProductCount();
    }

    private String cataloguePage(String query) {
        return client.get().uri("/catalogue?q={q}", query)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
