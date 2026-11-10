package ca.carletonu.controllers;

import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: starts the app on a random port and requests the landing
 * page over real HTTP, with the real H2 database behind it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class LandingControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @BeforeEach
    void clearShops() {
        shopRepository.deleteAll();
    }

    @Test
    void landingPageShowsEmptyStateWhenThereAreNoShops() {
        String body = getLandingPage();

        assertThat(body).contains("Mini-Shopify");
        assertThat(body).contains("0 shops on Mini-Shopify");
        assertThat(body).contains("No shops yet");
    }

    @Test
    void landingPageFeaturesShopsFromTheDatabase() {
        shopRepository.save(new Shop("Maple Books", "Second-hand books", "Books", null));

        String body = getLandingPage();

        assertThat(body).contains("Maple Books");
        assertThat(body).contains("Second-hand books");
        assertThat(body).contains("1 shop on Mini-Shopify");
        assertThat(body).doesNotContain("No shops yet");
    }

    @Test
    void landingPageFeaturesAtMostSixShops() {
        for (int i = 1; i <= LandingController.FEATURED_SHOP_COUNT + 2; i++) {
            shopRepository.save(new Shop("Shop " + i, "Description " + i, "General", null));
        }

        String body = getLandingPage();

        assertThat(body).contains("8 shops on Mini-Shopify");
        assertThat(body.split("class=\"shop-card\"", -1)).hasSize(LandingController.FEATURED_SHOP_COUNT + 1);
    }

    private String getLandingPage() {
        return client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
