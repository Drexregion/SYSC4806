package ca.carletonu.controllers;

import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ItemRepository;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the item details page .
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProductControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Test
    void itemPageShowsItemDetails() {
        Shop shop = new Shop("Lantern Works", "Handmade lamps", "Lighting", null);
        shop.addProduct(new Item("Paper lantern", "Folded rice paper", 1234.5, 4, "Lamp"));
        shop = shopRepository.save(shop);

        assertThat(getProductPage(productId(shop, "Paper lantern")))
                .contains("<title>Paper lantern - Mini-Shopify</title>")
                .contains("Paper lantern")
                .contains("Folded rice paper")
                .contains("$1,234.50")
                .contains("4 in stock")
                .contains("Lamp")
                .doesNotContain("Out of stock");
    }

    @Test
    void itemWithNoStockShowsOutOfStock() {
        Shop shop = new Shop("Lantern Works", "Handmade lamps", "Lighting", null);
        shop.addProduct(new Item("Brass lamp", "Polished brass", 89.0, 0, "Lamp"));
        shop = shopRepository.save(shop);

        assertThat(getProductPage(productId(shop, "Brass lamp")))
                .contains("Out of stock")
                .doesNotContain("in stock<");
    }

    @Test
    void servicePageShowsDuration() {
        Shop shop = new Shop("Lantern Works", "Handmade lamps", "Lighting", null);
        shop.addProduct(new Service("Lamp repair", "Rewiring and repair", 30.0, 45));
        shop = shopRepository.save(shop);

        assertThat(getProductPage(productId(shop, "Lamp repair")))
                .contains("Lamp repair")
                .contains("$30.00")
                .contains("45 min service")
                .doesNotContain("in stock")
                .doesNotContain("Out of stock");
    }

    @Test
    void itemPageShowsTheShopThatSellsIt() {
        Seller seller = sellerRepository.save(new Seller("Ada Okafor", "ada.items@example.com", "secret", "555-0100"));
        Shop shop = new Shop("Kettle Corner", "Teas from everywhere", "Tea", seller);
        shop.addProduct(new Item("Green tea", "Loose leaf sencha", 9.99, 10, "Tea"));
        shop = shopRepository.save(shop);

        assertThat(getProductPage(productId(shop, "Green tea")))
                .contains("Sold by")
                .contains("Kettle Corner")
                .contains("Teas from everywhere")
                .contains("Run by Ada Okafor");
    }

    @Test
    void itemPageLinksBackToItsShop() {
        Shop shop = new Shop("Fernhill Plants", "Houseplants", "Garden", null);
        shop.addProduct(new Item("Monstera", "Big leaves", 40.0, 2, "Plant"));
        shop = shopRepository.save(shop);

        assertThat(getProductPage(productId(shop, "Monstera")))
                .contains("href=\"/shops/" + shop.getId() + "\"")
                .contains("Back to Fernhill Plants");
    }

    @Test
    void itemPageShowsTheRightShopWhenThereAreSeveral() {
        Shop first = new Shop("First Shop", "One", "A", null);
        first.addProduct(new Item("First thing", "From the first shop", 1.0, 1, "Thing"));
        shopRepository.save(first);

        Shop second = new Shop("Second Shop", "Two", "B", null);
        second.addProduct(new Item("Second thing", "From the second shop", 2.0, 1, "Thing"));
        second = shopRepository.save(second);

        assertThat(getProductPage(productId(second, "Second thing")))
                .contains("Second Shop")
                .doesNotContain("First Shop");
    }

    @Test
    void productWithoutAShopStillRenders() {
        Item orphan = itemRepository.save(new Item("Loose item", null, 5.0, 1, null));

        assertThat(getProductPage(orphan.getId()))
                .contains("Loose item")
                .contains("No description provided.")
                .doesNotContain("Sold by")
                .doesNotContain("Back to");
    }

    @Test
    void unknownProductReturnsNotFound() {
        client.get().uri("/products/{id}", 999_999L)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void nonNumericProductIdIsABadRequest() {
        client.get().uri("/products/not-a-number")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shopPageLinksToEachProductsDetailsPage() {
        Shop shop = new Shop("Quiet Quill", "Pens", "Stationery", null);
        shop.addProduct(new Item("Fountain pen", "Smooth nib", 25.0, 3, "Pen"));
        shop = shopRepository.save(shop);

        String shopPage = client.get().uri("/shops/{id}", shop.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(shopPage).contains("href=\"/products/" + productId(shop, "Fountain pen") + "\"");
    }

    private Long productId(Shop shop, String name) {
        return shop.getProducts().stream()
                .filter(p -> p.getName().equals(name))
                .map(Product::getId)
                .findFirst()
                .orElseThrow();
    }

    private String getProductPage(Long id) {
        return client.get().uri("/products/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
