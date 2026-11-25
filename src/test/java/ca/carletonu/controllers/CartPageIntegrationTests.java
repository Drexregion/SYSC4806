package ca.carletonu.controllers;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.repositories.CartItemRepository;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CartPageIntegrationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private CurrentCustomer currentCustomer;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer shopper;
    private Product lamp;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        shopper = currentCustomer.find().orElseThrow();
        emptyCarts();
        lamp = productRepository.save(new Item("Cart Page Lamp", "Brass", 89.0, 3, "Lamp"));
        rewiring = productRepository.save(new Service("Cart Page Rewiring", "Made safe", 45.5, 45));
    }

    @AfterEach
    void tearDown() {
        emptyCarts();
        productRepository.delete(lamp);
        productRepository.delete(rewiring);
    }

    @Test
    void anEmptyCartShowsAMessageInsteadOfATableAndWritesNoCart() {
        assertThat(cartPage(null))
                .contains("Your cart is empty.")
                .doesNotContain("<table");
        assertThat(cartRepository.findByCustomerId(shopper.getId())).isEmpty();
    }

    @Test
    void showsEveryLineWithItsTotalsAndTheCartTotal() {
        add(lamp, 2);
        add(rewiring, 1);

        String body = cartPage(null);

        assertThat(body.split("class=\"cart-line\"", -1)).hasSize(3);
        assertThat(body)
                .contains("Cart Page Lamp", "$89.00", "value=\"2\"", "$178.00")
                .contains("Cart Page Rewiring", "$45.50")
                .contains("<strong>$223.50</strong>")
                .contains("max=\"3\"")
                .doesNotContain("Your cart is empty.");
    }

    @Test
    void changingAQuantityRedirectsToTheCartAndUpdatesTheTotals() {
        add(lamp, 1);

        String cookie = cookieFrom(changeQuantity(lamp, "3"));

        assertThat(cartPage(cookie)).contains("Cart updated.", "<strong>$267.00</strong>");
    }

    @Test
    void moreThanTheStockIsRefusedWithAMessageAndTheLineIsUnchanged() {
        add(lamp, 2);

        String cookie = cookieFrom(changeQuantity(lamp, "4"));

        assertThat(cartPage(cookie)).contains("Only 3 of Cart Page Lamp in stock.", "<strong>$178.00</strong>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", ""})
    void aQuantityBelowOneOrBlankIsRefused(String quantity) {
        add(lamp, 2);

        String cookie = cookieFrom(changeQuantity(lamp, quantity));

        assertThat(cartPage(cookie)).contains("Choose a quantity of at least one.", "<strong>$178.00</strong>");
    }

    @Test
    void removingALineRedirectsToTheCartAndLeavesTheOthers() {
        add(lamp, 1);
        add(rewiring, 1);

        String cookie = cookieFrom(remove(lamp));

        assertThat(cartPage(cookie))
                .contains("Removed from your cart.", "Cart Page Rewiring", "<strong>$45.50</strong>")
                .doesNotContain("Cart Page Lamp");
        Long cartId = cartRepository.findOpenCart(shopper).orElseThrow().getId();
        assertThat(cartItemRepository.findByCartId(cartId)).hasSize(1);
    }

    @Test
    void removingTheLastLineShowsTheEmptyCart() {
        add(lamp, 1);

        remove(lamp);

        assertThat(cartPage(null)).contains("Your cart is empty.");
    }

    @Test
    void removingAProductThatIsNotInTheCartShowsAMessage() {
        String cookie = cookieFrom(remove(lamp));

        assertThat(cartPage(cookie)).contains("That product is not in your cart.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/shops", "/shops/search", "/catalogue", "/cart"})
    void theNavigationLinksToTheCart(String path) {
        client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("href=\"/cart\""));
    }

    private void add(Product product, int quantity) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("productId", String.valueOf(product.getId()));
        form.add("quantity", String.valueOf(quantity));
        client.post().uri("/cart/items")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    private RestTestClient.ResponseSpec changeQuantity(Product product, String quantity) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("quantity", quantity);
        return client.post().uri("/cart/items/{id}/quantity", product.getId())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/cart$");
    }

    private RestTestClient.ResponseSpec remove(Product product) {
        return client.post().uri("/cart/items/{id}/remove", product.getId())
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/cart$");
    }

    private static String cookieFrom(RestTestClient.ResponseSpec response) {
        String setCookie = response.expectBody().returnResult()
                .getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private String cartPage(String cookie) {
        RestTestClient.RequestHeadersSpec<?> request = client.get().uri("/cart");
        if (cookie != null) {
            request = request.header(HttpHeaders.COOKIE, cookie);
        }
        return request.exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private void emptyCarts() {
        cartRepository.deleteAll(cartRepository.findByCustomerId(shopper.getId()));
    }
}
