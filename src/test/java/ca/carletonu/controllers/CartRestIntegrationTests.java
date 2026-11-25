package ca.carletonu.controllers;

import ca.carletonu.models.Cart;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.repositories.CartItemRepository;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carts over real HTTP.
 *
 * Issue #38 adds no controller or view, so the HTTP path a client has today is
 * the one Spring Data REST exposes for the repositories under /api. These
 * check that a cart and its lines are readable there, and that the two rules
 * hold even for a client posting straight to it: one open cart per customer,
 * and no line with a quantity of zero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CartRestIntegrationTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer bo;
    private Product lamp;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        bo = customerRepository.save(new Customer("Bo", "bo-cart@example.com", "pw", "555", "1 Main St"));
        lamp = productRepository.save(new Item("Cart Lamp", "Brass", 89.0, 4, "Lighting"));
        rewiring = productRepository.save(new Service("Cart Rewiring", "Make it safe", 45.0, 45));
    }

    @AfterEach
    void removeWhatThisTestMade() {
        cartRepository.findByCustomerId(bo.getId()).forEach(cartRepository::delete);
        customerRepository.delete(bo);
        productRepository.delete(lamp);
        productRepository.delete(rewiring);
    }

    @Test
    void aCartAndItsLinesAreReadableOverHttp() {
        Cart cart = cartRepository.openCartFor(bo);
        cart.addProduct(lamp, 2);
        cart.addProduct(rewiring, 1);
        cart = cartRepository.save(cart);

        assertThat(get("/api/carts/" + cart.getId()))
                .contains("\"status\" : \"OPEN\"")
                .contains("\"itemCount\" : 3")
                .contains("\"total\" : 223.0");

        assertThat(get("/api/carts/" + cart.getId() + "/items"))
                .contains("\"quantity\" : 2")
                .contains("\"quantity\" : 1");

        assertThat(get("/api/carts/" + cart.getId() + "/customer"))
                .contains("bo-cart@example.com");
    }

    @Test
    void aFirstCartAndALineCanBePostedOverHttp() {
        // The same requests as the refused ones below, but valid, so those
        // fail for the rule they test and not for a malformed body
        client.post().uri("/api/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customer\": \"/api/customers/" + bo.getId() + "\"}")
                .exchange()
                .expectStatus().isCreated();

        Cart cart = cartRepository.findOpenCart(bo).orElseThrow();

        client.post().uri("/api/cartItems")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"cart\": \"/api/carts/" + cart.getId() + "\","
                        + " \"product\": \"/api/products/" + lamp.getId() + "\","
                        + " \"quantity\": 2}")
                .exchange()
                .expectStatus().isCreated();

        assertThat(cartItemRepository.findByCartId(cart.getId()))
                .singleElement()
                .satisfies(line -> assertThat(line.getQuantity()).isEqualTo(2));
    }

    @Test
    void aSecondOpenCartPostedOverHttpIsRefused() {
        cartRepository.openCartFor(bo);

        client.post().uri("/api/carts")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"customer\": \"/api/customers/" + bo.getId() + "\"}")
                .exchange()
                .expectStatus().is4xxClientError();

        assertThat(cartRepository.findByCustomerId(bo.getId())).hasSize(1);
    }

    @Test
    void aZeroQuantityLinePostedOverHttpIsRefused() {
        Cart cart = cartRepository.openCartFor(bo);

        client.post().uri("/api/cartItems")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"cart\": \"/api/carts/" + cart.getId() + "\","
                        + " \"product\": \"/api/products/" + lamp.getId() + "\","
                        + " \"quantity\": 0}")
                .exchange()
                .expectStatus().is4xxClientError();

        assertThat(cartItemRepository.findByCartId(cart.getId())).isEmpty();
    }

    private String get(String path) {
        return client.get().uri(path)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
