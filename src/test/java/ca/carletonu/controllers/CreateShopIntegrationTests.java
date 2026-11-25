package ca.carletonu.controllers;

import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Creating a shop through the form, over real HTTP against the real database.
 *
 * The acceptance criterion on #16 is that a shop created through the form is
 * persisted and shows up, so that is what this checks. The application runs
 * without the "kafka" profile here, which is also how it runs on Azure: the
 * shop is still created, the event just is not published.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class CreateShopIntegrationTests {

    private static final String MARKER = "Zephyr Ironworks";

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @AfterEach
    void removeWhatThisTestMade() {
        shopsNamed(MARKER).forEach(shopRepository::delete);
    }

    @Test
    void aShopCreatedThroughTheFormIsPersistedAndShowsUp() {
        Long sellerId = aSeller();

        postFormExpectingRedirect(sellerId, form(MARKER, "Hand forged tools", "Metalwork"), ".*/$");

        List<Shop> created = shopsNamed(MARKER);
        assertThat(created).singleElement().satisfies(shop -> {
            assertThat(shop.getName()).isEqualTo(MARKER);
            assertThat(shop.getDescription()).isEqualTo("Hand forged tools");
            assertThat(shop.getType()).isEqualTo("Metalwork");
            assertThat(shop.getSeller()).isNotNull();
            assertThat(shop.getSeller().getId()).isEqualTo(sellerId);
        });

        // And it is reachable, which is the half that matters to a customer.
        assertThat(page("/shops/" + created.getFirst().getId())).contains(MARKER);
    }

    @Test
    void aBlankNameIsRejectedAndNothingIsSaved() {
        Long sellerId = aSeller();
        long before = shopRepository.count();

        postFormExpectingRedirect(sellerId, form("   ", "No name at all", "Metalwork"),
                ".*/sellers/" + sellerId + "/shops/new$");

        assertThat(shopRepository.count()).isEqualTo(before);
    }

    @Test
    void aBlankCategoryIsRejectedAndNothingIsSaved() {
        Long sellerId = aSeller();
        long before = shopRepository.count();

        postFormExpectingRedirect(sellerId, form(MARKER, "No category", ""),
                ".*/sellers/" + sellerId + "/shops/new$");

        assertThat(shopRepository.count()).isEqualTo(before);
    }

    @Test
    void anUnknownSellerIsSentHomeWithoutA500() {
        long before = shopRepository.count();

        postFormExpectingRedirect(Long.MAX_VALUE, form(MARKER, "No such seller", "Metalwork"), ".*/$");

        assertThat(shopRepository.count()).isEqualTo(before);
    }

    @Test
    void theFormPageRendersForARealSeller() {
        Long sellerId = aSeller();

        assertThat(page("/sellers/" + sellerId + "/shops/new"))
                .contains("Create your shop")
                .contains("action=\"/sellers/" + sellerId + "/shops\"");
    }

    @Test
    void theLandingPageLinksToTheFormRatherThanToNull() {
        aSeller();

        String landing = page("/");

        assertThat(landing).contains("/shops/new");
        assertThat(landing).doesNotContain("/null/");
    }

    private Long aSeller() {
        return sellerRepository
                .save(new Seller("Form Tester", "form.tester@example.com", "secret", "555-0133"))
                .getId();
    }

    private List<Shop> shopsNamed(String name) {
        return shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(name, name);
    }

    private MultiValueMap<String, String> form(String name, String description, String type) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("description", description);
        form.add("type", type);
        return form;
    }

    /** Submits the create-shop form and checks where it redirects to. */
    private void postFormExpectingRedirect(Long sellerId,
                                           MultiValueMap<String, String> form,
                                           String locationPattern) {
        client.post().uri("/sellers/{id}/shops", sellerId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection()
                // Tomcat makes the redirect absolute, so match the path at the end
                .expectHeader().valueMatches("Location", locationPattern);
    }

    private String page(String uri) {
        return client.get().uri(uri)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
