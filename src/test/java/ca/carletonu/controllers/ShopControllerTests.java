package ca.carletonu.controllers;

import ca.carletonu.models.Item;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ShopControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Test
    void shopPageShowsShopDetails() {
        Seller seller = sellerRepository.save(new Seller("Ada Okafor", "ada@example.com", "secret", "555-0100"));
        Shop shop = shopRepository.save(new Shop("Kettle Corner", "Teas from everywhere", "Tea", seller));

        assertThat(getShopPage(shop.getId()))
                .contains("Kettle Corner")
                .contains("Teas from everywhere")
                .contains("Tea")
                .contains("Run by Ada Okafor");
    }

    @Test
    void shopPageListsItsProducts() {
        Shop shop = new Shop("Lantern Works", "Handmade lamps", "Lighting", null);
        shop.addProduct(new Item("Paper lantern", "Folded rice paper", 12.5, 4, "Lamp"));
        shop.addProduct(new Item("Brass lamp", "Polished brass", 89.0, 0, "Lamp"));
        shop.addProduct(new Service("Lamp repair", "Rewiring and repair", 30.0, 45));
        shop = shopRepository.save(shop);

        assertThat(getShopPage(shop.getId()))
                .contains("3 products")
                .contains("Paper lantern")
                .contains("$12.50")
                .contains("4 in stock")
                .contains("Brass lamp")
                .contains("Out of stock")
                .contains("Lamp repair")
                .contains("45 min service")
                .doesNotContain("This shop has no products yet.");
    }

    @Test
    void shopWithNoProductsShowsEmptyState() {
        Shop shop = shopRepository.save(new Shop("Quiet Quill", "Pens, soon", "Stationery", null));

        assertThat(getShopPage(shop.getId()))
                .contains("0 products")
                .contains("This shop has no products yet.");
    }

    @Test
    void unknownShopReturnsNotFound() {
        client.get().uri("/shops/{id}", 999_999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void landingPageLinksToTheShopPage() {
        shopRepository.deleteAll();
        Shop shop = shopRepository.save(new Shop("Fernhill Plants", "Houseplants", "Garden", null));

        String landing = client.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(landing).contains("href=\"/shops/" + shop.getId() + "\"");
    }

    private String getShopPage(Long id) {
        return client.get().uri("/shops/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
