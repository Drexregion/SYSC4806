package ca.carletonu.readmodel;

import ca.carletonu.events.ShopCreatedEvent;
import ca.carletonu.messaging.KafkaProducer;
import ca.carletonu.models.Seller;
import ca.carletonu.models.repositories.SellerRepository;
import ca.carletonu.testsupport.KafkaEvents;
import ca.carletonu.testsupport.KafkaIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static ca.carletonu.testsupport.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read model fed through a real broker.
 *
 * ShopSummaryProjectionTests calls the projection directly, which says nothing
 * about the producer, the topic, serialization or the listener. These cover
 * that path from the form to the catalogue page.
 */
@KafkaIntegrationTest
class ShopSummaryKafkaTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ShopSummaryRepository shopSummaryRepository;

    @Autowired
    private KafkaProducer producer;

    @Autowired
    private KafkaEvents events;

    @Test
    void aShopCreatedThroughTheFormReachesTheCatalogue() {
        Long sellerId = aSeller();

        submitShopForm(sellerId, "Ember & Oak");

        eventually(() -> assertThat(cataloguePage()).contains("Ember &amp; Oak"));

        assertThat(shopSummaryRepository.findAll())
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getShopId()).isNotNull();
                    assertThat(summary.getSellerId()).isEqualTo(sellerId);
                    assertThat(summary.getName()).isEqualTo("Ember & Oak");
                    assertThat(summary.getDescription()).isEqualTo("Cast iron cookware");
                    assertThat(summary.getType()).isEqualTo("Kitchen");
                });
    }

    @Test
    void anEventDeliveredTwiceIsProjectedOnce() {
        ShopCreatedEvent event = new ShopCreatedEvent(1001L, 1L, "Tide & Timber", "Driftwood", "Home");

        producer.sendShopCreatedEvent(event);
        producer.sendShopCreatedEvent(event);
        events.awaitProcessed();

        assertThat(shopSummaryRepository.count()).isEqualTo(1);
    }

    @Test
    void aRejectedShopIsNeverProjected() {
        submitShopForm(aSeller(), "   ");
        events.awaitProcessed();

        assertThat(shopSummaryRepository.count()).isZero();
    }

    /**
     * Publishes without waiting, so the second repetition fails if the first
     * one's events can still arrive after the database was emptied.
     */
    @RepeatedTest(2)
    void eventsFromAnEarlierTestDoNotArriveInThisOne() {
        events.awaitProcessed();
        assertThat(shopSummaryRepository.count()).isZero();

        for (long shopId = 1; shopId <= 50; shopId++) {
            producer.sendShopCreatedEvent(new ShopCreatedEvent(shopId, 1L, "Shop " + shopId, "Late", "Home"));
        }
    }

    private Long aSeller() {
        return sellerRepository.save(new Seller("Ada", "ada@example.com", "pw", "555")).getId();
    }

    private void submitShopForm(Long sellerId, String name) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("description", "Cast iron cookware");
        form.add("type", "Kitchen");

        client.post().uri("/sellers/{id}/shops", sellerId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    private String cataloguePage() {
        return client.get().uri("/catalogue")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
