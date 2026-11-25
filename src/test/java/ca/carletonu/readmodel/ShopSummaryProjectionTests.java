package ca.carletonu.readmodel;

import ca.carletonu.events.ShopCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ShopSummaryProjectionTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopSummaryRepository shopSummaryRepository;

    @Autowired
    private ShopSummaryProjection projection;

    @BeforeEach
    @AfterEach
    void clearReadModel() {
        shopSummaryRepository.deleteAll();
    }

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
        // Nothing has been projected, so the catalogue is empty even though the
        // seeded shops exist in the write model. That is the separation.
        assertThat(cataloguePage(""))
                .contains("The read model is empty");
    }

    private ShopCreatedEvent event(Long shopId, String name, String description, String type) {
        return new ShopCreatedEvent(shopId, 1L, name, description, type);
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
