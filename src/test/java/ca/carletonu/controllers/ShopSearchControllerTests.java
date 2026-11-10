package ca.carletonu.controllers;

import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for shop search (#11), driven over real HTTP against the
 * running application.
 *
 * The shops use unusual names and categories, so seed data or rows left by
 * other tests in the shared in-memory database cannot match the searches.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ShopSearchControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @BeforeEach
    void setUp() {
        if (shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase("Zangetsu", "Zangetsu").isEmpty()) {
            shopRepository.save(new Shop("Zangetsu", "Swords", "Bladesmith", null));
            shopRepository.save(new Shop("Dragon Balls", "Collectibles", "Orbcraft", null));
        }
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

    private String search(String term) {
        return client.get().uri("/shops/search?q={q}", term)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
