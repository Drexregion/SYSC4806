package ca.carletonu.controllers;

import ca.carletonu.models.Cart;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkout over real HTTP, the path a shopper takes: add to the cart, check
 * out, land back on the cart page with a message saying what happened.
 *
 * The shopper is whoever CurrentCustomer stands in, the demo customer the
 * seeder writes. Each test empties that customer's carts before and after.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CheckoutIntegrationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private CurrentCustomer currentCustomer;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer shopper;
    private Shop shop;
    private Product lamp;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        shopper = currentCustomer.find().orElseThrow();
        emptyCarts();

        lamp = productRepository.save(new Item("Checkout Test Lamp", "Brass", 89.0, 3, "Lamp"));
        rewiring = productRepository.save(new Service("Checkout Test Rewiring", "Made safe", 45.0, 45));

        // Saved empty first, then with its products, the same way as in
        // AddToCartIntegrationTests: persist cannot cascade onto saved products.
        shop = shopRepository.save(new Shop("Checkout Test Lamps", "Lamps for checkout tests", "Lighting", null));
        shop.addProduct(lamp);
        shop.addProduct(rewiring);
        shop = shopRepository.save(shop);
    }

    @AfterEach
    void tearDown() {
        emptyCarts();
        shopRepository.delete(shop);
    }

    @Test
    void checkingOutClosesTheCartTakesStockAndSaysWhatWasBought() {
        addToCart(lamp, 2);
        addToCart(rewiring, 1);

        String cookie = sessionCookieFrom(checkout()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/cart$"));

        assertThat(cartRepository.findOpenCart(shopper)).isEmpty();
        assertThat(cartsOf(shopper)).singleElement()
                .satisfies(cart -> assertThat(cart.isOpen()).isFalse());
        assertThat(stockOf(lamp)).isEqualTo(1);

        // The cart page now shows no lines, since the open cart is gone
        assertThat(page("/cart", cookie))
                .contains("Checked out 3 items for $223.00.")
                .doesNotContain("Checkout Test Lamp");
    }

    @Test
    void anEmptyCartIsRefusedWithAMessageAndWritesNothing() {
        String cookie = sessionCookieFrom(checkout());

        assertThat(cartsOf(shopper)).isEmpty();
        assertThat(page("/cart", cookie)).contains("Your cart is empty.");
    }

    @Test
    void stockThatFellSinceTheAddIsRefusedAndTheCartStaysOpen() {
        addToCart(lamp, 2);

        // Someone else bought most of them in the meantime
        Item stored = (Item) productRepository.findById(lamp.getId()).orElseThrow();
        stored.setQuantity(1);
        productRepository.save(stored);

        String cookie = sessionCookieFrom(checkout());

        assertThat(cartRepository.findOpenCart(shopper)).isPresent();
        assertThat(stockOf(lamp)).isEqualTo(1);
        // Still on the cart page with the line, so the shopper can lower it
        assertThat(page("/cart", cookie))
                .contains("Your cart has more than is in stock: only 1 of Checkout Test Lamp left.")
                .contains("Checkout Test Lamp");
    }

    @Test
    void afterCheckingOutTheNextAddOpensANewCart() {
        addToCart(lamp, 1);
        checkout().expectStatus().is3xxRedirection();

        addToCart(lamp, 1);

        assertThat(cartsOf(shopper)).hasSize(2);
        assertThat(cartRepository.findOpenCart(shopper)).isPresent();
    }

    private void addToCart(Product product, int quantity) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("productId", String.valueOf(product.getId()));
        form.add("quantity", String.valueOf(quantity));

        client.post().uri("/cart/items")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    private RestTestClient.ResponseSpec checkout() {
        return client.post().uri("/cart/checkout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(new LinkedMultiValueMap<String, String>())
                .exchange();
    }

    /** The page as the shopper sees it after the redirect, flash message included. */
    private String page(String path, String cookie) {
        return client.get().uri(path)
                .header(HttpHeaders.COOKIE, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    /** The session cookie a response set, so the next request sees its flash message. */
    private static String sessionCookieFrom(RestTestClient.ResponseSpec response) {
        String setCookie = response.expectStatus().is3xxRedirection()
                .expectBody().returnResult()
                .getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private int stockOf(Product product) {
        return ((Item) productRepository.findById(product.getId()).orElseThrow()).getQuantity();
    }

    private List<Cart> cartsOf(Customer customer) {
        return cartRepository.findByCustomerId(customer.getId());
    }

    private void emptyCarts() {
        cartRepository.deleteAll(cartRepository.findByCustomerId(shopper.getId()));
    }
}
