package ca.carletonu.controllers;

import ca.carletonu.models.Cart;
import ca.carletonu.models.Customer;
import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.Transaction;
import ca.carletonu.models.repositories.CartRepository;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.ProductRepository;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.models.repositories.TransactionRepository;
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
 * Checkout over real HTTP, the path a shopper takes: add to the cart, place
 * the order from the cart page, and land on a confirmation page that lists
 * what was bought.
 *
 * The shopper is whoever CurrentCustomer stands in, the demo customer the
 * seeder writes. Each test removes that customer's purchases and carts before
 * and after, purchases first because they point at the products.
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
    private CustomerRepository customerRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Customer shopper;
    private Shop shop;
    private Product lamp;
    private Product rewiring;

    @BeforeEach
    void setUp() {
        shopper = currentCustomer.find().orElseThrow();
        clearPurchasesAndCarts(shopper);

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
        clearPurchasesAndCarts(shopper);
        shopRepository.delete(shop);
    }

    @Test
    void checkingOutClosesTheCartTakesStockAndShowsTheOrder() {
        addToCart(lamp, 2);
        addToCart(rewiring, 1);

        String location = confirmationPathFrom(checkout());

        assertThat(cartRepository.findOpenCart(shopper)).isEmpty();
        assertThat(cartsOf(shopper)).singleElement()
                .satisfies(cart -> assertThat(cart.isOpen()).isFalse());
        assertThat(stockOf(lamp)).isEqualTo(1);

        assertThat(page(location))
                .contains("Thanks, your order is placed")
                .contains("Checkout Test Lamp")
                .contains("Checkout Test Rewiring")
                .contains("Checkout Test Lamps")
                .contains("$223.00");
    }

    @Test
    void eachLineIsSavedAsATransaction() {
        addToCart(lamp, 2);
        addToCart(rewiring, 1);
        Long cartId = cartRepository.findOpenCart(shopper).orElseThrow().getId();

        checkout().expectStatus().is3xxRedirection();

        List<Transaction> purchases = transactionRepository.findByCartIdOrderByIdAsc(cartId);
        assertThat(purchases).hasSize(2).allSatisfy(purchase -> {
            assertThat(purchase.getCustomer().getId()).isEqualTo(shopper.getId());
            assertThat(purchase.getShop().getId()).isEqualTo(shop.getId());
            assertThat(purchase.getDate()).isNotNull();
        });
        assertThat(purchases).extracting(purchase -> purchase.getProduct().getId())
                .containsExactly(lamp.getId(), rewiring.getId());
        assertThat(purchases).extracting(Transaction::getQuantity).containsExactly(2, 1);
        assertThat(purchases).extracting(Transaction::getUnitPrice).containsExactly(89.0, 45.0);
    }

    @Test
    void theConfirmationPageStillShowsTheOrderAfterARefresh() {
        addToCart(lamp, 1);
        String location = confirmationPathFrom(checkout());

        assertThat(page(location)).contains("Checkout Test Lamp");
        assertThat(page(location)).contains("Checkout Test Lamp");
    }

    @Test
    void anOpenCartHasNoConfirmationPage() {
        addToCart(lamp, 1);
        Long openCartId = cartRepository.findOpenCart(shopper).orElseThrow().getId();

        client.get().uri("/checkout/confirmation/{id}", openCartId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void someoneElsesOrderIsNotFound() {
        Customer other = customerRepository.save(
                new Customer("Other Shopper", "other@example.com", "pw", "555", "2 Main St"));
        Cart theirs = new Cart(other);
        theirs.addProduct(lamp, 1);
        theirs.checkOut();
        theirs = cartRepository.save(theirs);

        try {
            client.get().uri("/checkout/confirmation/{id}", theirs.getId())
                    .exchange()
                    .expectStatus().isNotFound();
        } finally {
            cartRepository.delete(theirs);
            customerRepository.delete(other);
        }
    }

    @Test
    void anUnknownOrderIsNotFound() {
        client.get().uri("/checkout/confirmation/{id}", Long.MAX_VALUE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void anEmptyCartIsRefusedWithAMessageAndWritesNothing() {
        String cookie = sessionCookieFrom(checkout());

        assertThat(cartsOf(shopper)).isEmpty();
        assertThat(transactionRepository.findByCustomerOrderByDateDesc(shopper)).isEmpty();
        assertThat(page("/cart", cookie)).contains("Your cart is empty.");
    }

    @Test
    void stockThatFellSinceTheAddIsRefusedAndNothingIsWritten() {
        addToCart(lamp, 2);

        // Someone else bought most of them in the meantime
        Item stored = (Item) productRepository.findById(lamp.getId()).orElseThrow();
        stored.setQuantity(1);
        productRepository.save(stored);

        String cookie = sessionCookieFrom(checkout());

        assertThat(cartRepository.findOpenCart(shopper)).isPresent();
        assertThat(stockOf(lamp)).isEqualTo(1);
        assertThat(transactionRepository.findByCustomerOrderByDateDesc(shopper)).isEmpty();
        // Still on the cart page with the line, so the shopper can lower it
        assertThat(page("/cart", cookie))
                .contains("Checkout was refused: only 1 of Checkout Test Lamp left.")
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

    private String page(String path) {
        return page(path, null);
    }

    /** The page as the shopper sees it after the redirect, flash message included. */
    private String page(String path, String cookie) {
        RestTestClient.RequestHeadersSpec<?> request = client.get().uri(path);
        if (cookie != null) {
            request = request.header(HttpHeaders.COOKIE, cookie);
        }
        return request.exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    /** Where a successful checkout sent the shopper: its confirmation page, as a path. */
    private static String confirmationPathFrom(RestTestClient.ResponseSpec response) {
        String location = response.expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/checkout/confirmation/\\d+$")
                .expectBody().returnResult()
                .getResponseHeaders().getLocation().getPath();
        assertThat(location).startsWith("/checkout/confirmation/");
        return location;
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

    private void clearPurchasesAndCarts(Customer customer) {
        transactionRepository.deleteAll(transactionRepository.findByCustomerOrderByDateDesc(customer));
        cartRepository.deleteAll(cartRepository.findByCustomerId(customer.getId()));
    }
}
