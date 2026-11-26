package ca.carletonu.data;

import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.readmodel.ShopSummaryBackfill;
import ca.carletonu.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the startup demo data: that it writes something, that restarting
 * does not duplicate it, and that what it writes is enough to demonstrate the
 * browse and search pages. Each test starts with an empty database, so it
 * seeds for itself.
 */
@IntegrationTest
class DemoDataSeederTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private ShopSummaryBackfill backfill;

    @Test
    void seedsEnoughShopsToFillTheLandingPage() {
        seeder.seed();

        // The landing page features six, so there has to be more than six for
        // the all shops page to be showing anything the landing page does not.
        assertThat(shopRepository.count()).isGreaterThan(6);
    }

    @Test
    void startupDoesNotSeedTwice() {
        seeder.seed();
        long afterFirstRun = shopRepository.count();

        seeder.run(null);

        assertThat(shopRepository.count()).isEqualTo(afterFirstRun);
    }

    @Test
    void startupSeedsWhenTheDatabaseIsEmpty() {
        assertThat(shopRepository.count()).isZero();

        seeder.run(null);

        assertThat(shopRepository.count()).isGreaterThan(6);
    }

    @Test
    void seededShopsShowUpOnTheLandingPage() {
        seeder.seed();

        assertThat(getPage("/"))
                .contains("Kettle Corner")
                .doesNotContain("No shops yet");
    }

    @Test
    void seededShopsCanBeFoundByName() {
        seedAndProject();

        assertThat(getPage("/shops/search?q=fernhill"))
                .contains("Fernhill Plants")
                .doesNotContain("No shops match");
    }

    @Test
    void seededShopsCanBeFoundByCategory() {
        seedAndProject();

        assertThat(getPage("/shops/search?q=stationery"))
                .contains("Quiet Quill");
    }

    @Test
    void seedsAShopWithNoSellerSoThatCaseIsCovered() {
        seeder.seed();

        assertThat(shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase("Northside", "Northside"))
                .singleElement()
                .satisfies(shop -> assertThat(shop.getSeller()).isNull());
    }

    @Test
    void seedsOutOfStockItemsAndServicesNotJustStockedItems() {
        seeder.seed();

        String teaShop = getPage(shopUrl("Kettle Corner"));

        assertThat(teaShop)
                .contains("24 in stock")
                .contains("Out of stock")
                .contains("60 min service");
    }

    /**
     * Search reads the catalogue, and the seeder writes straight to the
     * repository with no event behind it, so the catalogue would not know
     * about these shops. The application runs the backfill immediately after
     * the seeder at startup; these tests do the same thing in the same order.
     */
    private void seedAndProject() {
        seeder.seed();
        backfill.backfill();
    }

    private String shopUrl(String shopName) {
        Long id = shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(shopName, shopName)
                .getFirst()
                .getId();
        return "/shops/" + id;
    }

    private String getPage(String uri) {
        return client.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
