package ca.carletonu.controllers;

import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.readmodel.ShopSummaryBackfill;
import ca.carletonu.testsupport.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for shop search (#11), driven over real HTTP against the
 * running application.
 */
@IntegrationTest
class ShopSearchControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ShopSummaryBackfill backfill;

    @BeforeEach
    void setUp() {
        shopRepository.save(new Shop("Zangetsu", "Swords", "Bladesmith", null));
        shopRepository.save(new Shop("Dragon Balls", "Collectibles", "Orbcraft", null));
    }

    @Test
    void searchByNameReturnsMatchingShop() {
        assertThat(search("zangetsu"))
                .contains("Zangetsu")
                .doesNotContain("Dragon Balls");
    }

    @Test
    void searchByCategoryReturnsMatchingShop() {
        assertThat(search("bladesmith"))
                .contains("Zangetsu")
                .doesNotContain("Dragon Balls");
    }

    @Test
    void searchWithNoMatchShowsEmptyState() {
        assertThat(search("zzxqvj"))
                .contains("No shops match")
                .doesNotContain("Zangetsu")
                .doesNotContain("Dragon Balls");
    }

    @Test
    void blankSearchAsksForATermInsteadOfListingEveryShop() {
        assertThat(search(""))
                .contains("Enter a shop name or category.")
                .doesNotContain("Zangetsu");
    }

    @Test
    void resultLinksToTheShopPage() {
        Long id = shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase("Zangetsu", "Zangetsu")
                .getFirst().getId();

        assertThat(search("zangetsu")).contains("href=\"/shops/" + id + "\"");
    }

    /**
     * Search reads the catalogue, and these shops were written straight to the
     * repository with no event behind them, so let the projection catch up
     * first. The application does the same thing at startup.
     */
    private String search(String term) {
        backfill.backfill();
        return client.get().uri("/shops/search?q={q}", term)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
