package ca.carletonu.controllers;

import ca.carletonu.models.Cart;
import ca.carletonu.models.CartItem;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.CartItemRepository;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.ProductRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Add to cart over real HTTP, the path a shopper takes: press the button on a
 * shop or item page, land back on that page, and find the product in the cart.
 *
 * The shopper is whoever CurrentCustomer stands in, which is the demo customer
 * the seeder writes at startup. Each test empties that customer's carts before
 * and after, so the tests do not see each other's lines.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AddToCartIntegrationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private CurrentCustomer currentCustomer;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer shopper;
    private Shop shop;
    private Product lamp;
    private Product soldOut;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        shopper = currentCustomer.find().orElseThrow();
        emptyCarts();

        // Saved on their own first so their ids are known here, then put in a
        // shop so the shop page has them to show.
        lamp = productRepository.save(new Item("Cart Test Lamp", "Brass", 89.0, 2, "Lamp"));
        soldOut = productRepository.save(new Item("Cart Test Sold Out", "None left", 12.0, 0, "Lamp"));
        rewiring = productRepository.save(new Service("Cart Test Rewiring", "Made safe", 45.0, 45));

        // The shop is saved empty first: saving a new shop persists it, and
        // persist cannot cascade onto products that are already saved. Once it
        // has an id the second save merges, which can.
        shop = shopRepository.save(new Shop("Cart Test Lamps", "Lamps for the cart tests", "Lighting", null));
        shop.addProduct(lamp);
        shop.addProduct(soldOut);
        shop.addProduct(rewiring);
        shop = shopRepository.save(shop);
    }

    @AfterEach
    void tearDown() {
        // Carts first: their lines point at this shop's products.
        emptyCarts();
        shopRepository.delete(shop);
    }

    @Test
    void addingFromTheShopPagePutsTheItemInTheCartAndReturnsToTheShop() {
        add(lamp, 1, "/shops/" + shop.getId())
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/shops/" + shop.getId() + "$");

        assertThat(openCartLines()).singleElement().satisfies(line -> {
            assertThat(line.getProduct().getId()).isEqualTo(lamp.getId());
            assertThat(line.getQuantity()).isEqualTo(1);
        });
    }

    @Test
    void addingFromTheItemPageReturnsToTheItem() {
        add(lamp, 1, "/products/" + lamp.getId())
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/products/" + lamp.getId() + "$");
    }

    @Test
    void addingTheSameProductTwiceRaisesTheQuantityInsteadOfAddingALine() {
        add(lamp, 1, null).expectStatus().is3xxRedirection();
        add(lamp, 1, null).expectStatus().is3xxRedirection();

        assertThat(openCartLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualTo(2));
    }

    @Test
    void addingMoreThanTheStockIsRefusedWithAMessageAndWritesNothing() {
        String cookie = sessionCookieFrom(add(lamp, 3, "/products/" + lamp.getId()));

        assertThat(cartRepository.findByCustomerId(shopper.getId())).isEmpty();

        // The refusal is a flash message, so it shows on the page the
        // redirect lands on, in the same session.
        client.get().uri("/products/{id}", lamp.getId())
                .header(HttpHeaders.COOKIE, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Only 2 of Cart Test Lamp in stock."));
    }

    @Test
    void aServiceIsAddedWithoutAStockCheck() {
        add(rewiring, 5, null).expectStatus().is3xxRedirection();

        assertThat(openCartLines()).singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualTo(5));
    }

    @Test
    void aSuccessfulAddShowsWhatWasAddedOnThePage() {
        String cookie = sessionCookieFrom(add(lamp, 1, "/shops/" + shop.getId()));

        client.get().uri("/shops/{id}", shop.getId())
                .header(HttpHeaders.COOKIE, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("Added 1 × Cart Test Lamp to your cart."));
    }

    @Test
    void anUnknownProductIsNotFound() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("productId", String.valueOf(Long.MAX_VALUE));

        client.post().uri("/cart/items")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void anOutsideReturnAddressIsIgnored() {
        add(lamp, 1, "https://example.com/elsewhere")
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/products/" + lamp.getId() + "$");
    }

    @Test
    void theShopPageOffersAddToCartExceptOnItemsWithNoStock() {
        client.get().uri("/shops/{id}", shop.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("name=\"productId\" value=\"" + lamp.getId() + "\"")
                        .contains("name=\"productId\" value=\"" + rewiring.getId() + "\"")
                        .doesNotContain("name=\"productId\" value=\"" + soldOut.getId() + "\""));
    }

    @Test
    void theItemPageOffersAQuantityCappedAtTheStock() {
        client.get().uri("/products/{id}", lamp.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("action=\"/cart/items\"")
                        .contains("name=\"quantity\"")
                        .contains("max=\"2\""));
    }

    @Test
    void anItemWithNoStockHasNoAddToCartOnItsPage() {
        client.get().uri("/products/{id}", soldOut.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("action=\"/cart/items\""));
    }

    private RestTestClient.ResponseSpec add(Product product, int quantity, String returnTo) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("productId", String.valueOf(product.getId()));
        form.add("quantity", String.valueOf(quantity));
        if (returnTo != null) {
            form.add("returnTo", returnTo);
        }

        return client.post().uri("/cart/items")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange();
    }

    /** The session cookie a response set, so the next request sees its flash message. */
    private static String sessionCookieFrom(RestTestClient.ResponseSpec response) {
        String setCookie = response.expectStatus().is3xxRedirection()
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    /** Read through the line repository: a cart's own list loads lazily, outside any transaction here. */
    private List<CartItem> openCartLines() {
        Optional<Cart> cart = cartRepository.findOpenCart(shopper);
        assertThat(cart).isPresent();
        return cartItemRepository.findByCartId(cart.get().getId());
    }

    private void emptyCarts() {
        cartRepository.deleteAll(cartRepository.findByCustomerId(shopper.getId()));
    }
}
