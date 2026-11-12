package ca.carletonu.controllers;

import ca.carletonu.models.Item;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AllShopsPageTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @BeforeEach
    void clearShops() {
        shopRepository.deleteAll();
    }

    @Test
    void showsEmptyStateWhenThereAreNoShops() {
        assertThat(getAllShopsPage())
                .contains("0 shops")
                .contains("No shops yet");
    }

    @Test
    void listsEveryShopWithItsDetails() {
        Shop books = new Shop("Maple Books", "Second-hand books", "Books", null);
        books.addProduct(new Item("Atlas", "1962 world atlas", 20.0, 1, "Book"));
        shopRepository.save(books);
        shopRepository.save(new Shop("Kettle Corner", "Teas from everywhere", "Tea", null));

        assertThat(getAllShopsPage())
                .contains("2 shops")
                .contains("Maple Books")
                .contains("Second-hand books")
                .contains("Books")
                .contains("1 product")
                .contains("Kettle Corner")
                .contains("Teas from everywhere")
                .contains("0 products")
                .doesNotContain("No shops yet");
    }

    @Test
    void listsMoreShopsThanTheLandingPageFeatures() {
        for (int i = 1; i <= LandingController.FEATURED_SHOP_COUNT + 2; i++) {
            shopRepository.save(new Shop("Shop " + i, "Description " + i, "General", null));
        }

        String body = getAllShopsPage();

        assertThat(body).contains("8 shops");
        assertThat(body.split("class=\"shop-card\"", -1)).hasSize(LandingController.FEATURED_SHOP_COUNT + 3);
    }

    @Test
    void sortsShopsByName() {
        shopRepository.save(new Shop("zebra Prints", "Posters", "Art", null));
        shopRepository.save(new Shop("Apple Orchard", "Fruit", "Food", null));
        shopRepository.save(new Shop("Maple Books", "Books", "Books", null));

        String body = getAllShopsPage();

        assertThat(body.indexOf("Apple Orchard"))
                .isLessThan(body.indexOf("Maple Books"));
        assertThat(body.indexOf("Maple Books"))
                .isLessThan(body.indexOf("zebra Prints"));
    }

    @Test
    void linksEachShopToItsPage() {
        Shop shop = shopRepository.save(new Shop("Lantern Works", "Lamps", "Lighting", null));

        assertThat(getAllShopsPage()).contains("href=\"/shops/" + shop.getId() + "\"");
    }

    @Test
    void includesTheShopSearchForm() {
        assertThat(getAllShopsPage())
                .contains("action=\"/shops/search\"")
                .contains("name=\"q\"");
    }

    @Test
    void landingPageLinksToAllShops() {
        String landing = client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(landing).contains("href=\"/shops\"");
    }

    private String getAllShopsPage() {
        return client.get().uri("/shops")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
