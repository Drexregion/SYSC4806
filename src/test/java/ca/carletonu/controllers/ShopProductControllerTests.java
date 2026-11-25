package ca.carletonu.controllers;

import ca.carletonu.models.Item;
import ca.carletonu.models.Product;
import ca.carletonu.models.Service;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for adding products to a shop, driven over real
 * HTTP against the running application.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ShopProductControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void addedProductIsSavedAgainstTheRightShop() {
        Shop shop = shopRepository.save(new Shop("Corner Store", "Snacks", "Grocery", null));
        Shop otherShop = shopRepository.save(new Shop("Football Store", "Sports equipment", "Sports", null));

        postProduct(shop.getId(), form("Granola Bar", "Oats and honey", "2.50", "40", "Snack"));

        List<Product> products = productsOf(shop.getId());
        assertThat(products).hasSize(1);
        assertThat(products.getFirst()).isInstanceOf(Item.class);

        Item item = (Item) products.getFirst();
        assertThat(item.getName()).isEqualTo("Granola Bar");
        assertThat(item.getDescription()).isEqualTo("Oats and honey");
        assertThat(item.getPrice()).isEqualTo(2.50);
        assertThat(item.getQuantity()).isEqualTo(40);
        assertThat(item.getProductType()).isEqualTo("Snack");

        assertThat(productsOf(otherShop.getId())).isEmpty();

        // The shop page the merchant is redirected to lists the new product and its inventory
        assertThat(shopPage(shop.getId()))
                .contains("Granola Bar")
                .contains("40 in stock");
    }

    @Test
    void shopPageShowsTheAddProductForm() {
        Shop shop = shopRepository.save(new Shop("Form Check Store", "Test", "Test", null));

        assertThat(shopPage(shop.getId()))
                .contains("Add a product")
                .contains("action=\"/shops/" + shop.getId() + "/products\"");
    }

    @Test
    void blankNameIsRejected() {
        Shop shop = shopRepository.save(new Shop("Random", "Test", "Test", null));

        postProduct(shop.getId(), form("  ", "No name", "1.00", "5", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void nonNumericInventoryIsRejected() {
        Shop shop = shopRepository.save(new Shop("Poorman Store", "Test", "Test", null));

        postProduct(shop.getId(), form("Widget", "Bad inventory", "1.00", "lots", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void negativeInventoryIsRejected() {
        Shop shop = shopRepository.save(new Shop("Negative Ten Store", "Test", "Test", null));

        postProduct(shop.getId(), form("Widget", "Negative inventory", "1.00", "-3", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void negativePriceIsRejected() {
        Shop shop = shopRepository.save(new Shop("Bargain Bin", "Test", "Test", null));

        postProduct(shop.getId(), form("Widget", "Negative price", "-1.00", "5", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void aProductAddedOverHttpGetsAnIdAndIsReachable() {
        // Runs without the kafka profile, as on Azure: no broker, and the
        // product is still saved. The id is the one the event would carry.
        Shop shop = shopRepository.save(new Shop("Id Check Store", "Test", "Test", null));

        postProduct(shop.getId(), form("Lantern", "Brass", "12.00", "3", "Home"));

        Product saved = productsOf(shop.getId()).getFirst();
        assertThat(saved.getId()).isNotNull();

        assertThat(client.get().uri("/products/{id}", saved.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody())
                .contains("Lantern");
    }

    @Test
    void aServiceAddedThroughTheFormIsSavedAsAService() {
        Shop shop = shopRepository.save(new Shop("Tea Room", "Tastings", "Tea", null));

        MultiValueMap<String, String> form = serviceForm("Guided Tasting", "Six teas", "35.00", "60");
        // The browser sends the hidden item fields too; they are ignored for a service
        form.add("quantity", "");
        form.add("productType", "");
        postProduct(shop.getId(), form);

        List<Product> products = productsOf(shop.getId());
        assertThat(products).singleElement().isInstanceOf(Service.class);

        Service service = (Service) products.getFirst();
        assertThat(service.getName()).isEqualTo("Guided Tasting");
        assertThat(service.getPrice()).isEqualTo(35.00);
        assertThat(service.getDuration()).isEqualTo(60);

        assertThat(shopPage(shop.getId())).contains("60 min service");
    }

    @Test
    void aServiceWithoutDurationIsRejected() {
        Shop shop = shopRepository.save(new Shop("Repair Shop", "Test", "Test", null));

        postProduct(shop.getId(), serviceForm("Rewiring", "No duration", "45.00", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void anItemWithoutInventoryIsRejected() {
        Shop shop = shopRepository.save(new Shop("Empty Shelf", "Test", "Test", null));

        postProduct(shop.getId(), form("Widget", "No inventory", "1.00", "", ""));

        assertThat(productsOf(shop.getId())).isEmpty();
    }

    @Test
    void theFormOffersBothKinds() {
        Shop shop = shopRepository.save(new Shop("Kind Check Store", "Test", "Test", null));

        assertThat(shopPage(shop.getId()))
                .contains("value=\"ITEM\"")
                .contains("value=\"SERVICE\"")
                .contains("name=\"duration\"");
    }

    @Test
    void unknownShopIsNotFound() {
        client.post().uri("/shops/{id}/products", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form("Widget", "No shop", "1.00", "1", ""))
                .exchange()
                .expectStatus().isNotFound();
    }

    private MultiValueMap<String, String> form(String name, String description, String price,
                                               String quantity, String productType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("description", description);
        form.add("price", price);
        form.add("quantity", quantity);
        form.add("productType", productType);
        return form;
    }

    private MultiValueMap<String, String> serviceForm(String name, String description, String price,
                                                      String duration) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("kind", "SERVICE");
        form.add("name", name);
        form.add("description", description);
        form.add("price", price);
        form.add("duration", duration);
        return form;
    }

    /** Submits the form and checks it redirects back to that shop's page. */
    private void postProduct(Long shopId, MultiValueMap<String, String> form) {
        client.post().uri("/shops/{id}/products", shopId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection()
                // Tomcat makes the redirect absolute, so match the path at the end
                .expectHeader().valueMatches("Location", ".*/shops/" + shopId + "$");
    }

    private String shopPage(Long shopId) {
        return client.get().uri("/shops/{id}", shopId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private List<Product> productsOf(Long shopId) {
        return transactionTemplate.execute(status ->
                List.copyOf(shopRepository.findById(shopId).orElseThrow().getProducts()));
    }
}
