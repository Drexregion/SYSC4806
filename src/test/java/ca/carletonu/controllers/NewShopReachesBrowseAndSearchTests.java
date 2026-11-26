package ca.carletonu.controllers;

import ca.carletonu.cqrs.commands.AddProductCommand;
import ca.carletonu.cqrs.handlers.AddProductCommandHandler;
import ca.carletonu.models.Seller;
import ca.carletonu.models.Shop;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.models.repositories.ShopRepository;
import ca.carletonu.readmodel.ShopSummaryRepository;
import ca.carletonu.testsupport.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browse and search read the catalogue read model, which is fed by events.
 * Under the "kafka" profile those events arrive over the broker. Without it,
 * which is how this test runs and how the app runs on Azure, they are
 * published in process instead.
 *
 * These tests exist because that second path was missing: the Kafka listeners
 * are profiled off, so nothing consumed the events at all and a shop created
 * after startup never reached the read model. The backfill did not cover it,
 * because the backfill only runs once at startup. A merchant could create a
 * shop and never find it by browsing or searching.
 */
@IntegrationTest
class NewShopReachesBrowseAndSearchTests {

    private static final String MARKER = "Thornwick Bellfounders";
    private static final String CATEGORY = "Bellfounding";

    @Autowired
    private RestTestClient client;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ShopSummaryRepository shopSummaryRepository;

    @Autowired
    private AddProductCommandHandler addProductCommandHandler;

    @Test
    void aShopCreatedWithNoBrokerRunningStillShowsUpInBrowse() {
        createShopThroughTheForm();

        assertThat(page("/shops")).contains(MARKER);
    }

    @Test
    void aShopCreatedWithNoBrokerRunningIsAlsoSearchable() {
        createShopThroughTheForm();

        assertThat(page("/shops/search?q=" + CATEGORY)).contains(MARKER);
        assertThat(page("/shops/search?q=Thornwick")).contains(MARKER);
    }

    @Test
    void aProductAddedAfterwardsIsCountedOnThatShopsCard() {
        Long shopId = createShopThroughTheForm();

        addProductCommandHandler.handle(
                AddProductCommand.item(shopId, "Tenor bell", "Cast bronze", 4200.0, 2, "Bell"));

        assertThat(shopSummaryRepository.findByShopId(shopId).orElseThrow().getProductCount())
                .isEqualTo(1);
    }

    /** Posts the create-shop form and returns the new shop's id. */
    private Long createShopThroughTheForm() {
        Long sellerId = sellerRepository
                .save(new Seller("Bell Tester", "bell.tester@example.com", "secret", "555-0144"))
                .getId();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", MARKER);
        form.add("description", "Church bells, cast to order");
        form.add("type", CATEGORY);

        client.post().uri("/sellers/{id}/shops", sellerId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection();

        return shopsNamed(MARKER).getFirst().getId();
    }

    private List<Shop> shopsNamed(String name) {
        return shopRepository.findByNameContainingIgnoreCaseOrTypeContainingIgnoreCase(name, name);
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
