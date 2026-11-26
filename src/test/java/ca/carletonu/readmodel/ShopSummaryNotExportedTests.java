package ca.carletonu.readmodel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The read model must not be reachable over the REST API.
 *
 * Spring Data REST publishes every repository under /api by default. Left as
 * it was, that let anyone POST a row into the catalogue or DELETE one, which
 * breaks the rule the CQRS split rests on: the read model is derived from the
 * event log and is never written to directly.
 *
 * ShopSummaryRepository is annotated exported = false. These two tests are
 * what would catch it if somebody removed that annotation later. The path
 * asserted here is the one named in the annotation, so if the repository were
 * exported again both of these would return 200 instead of 404.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ShopSummaryNotExportedTests {

    private static final String ENDPOINT = "/api/shop-summaries";

    @Autowired
    private RestTestClient client;

    @Test
    void theReadModelIsNotReadableOverTheApi() {
        client.get()
                .uri(ENDPOINT)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void theReadModelCannotBeWrittenToOverTheApi() {
        client.post()
                .uri(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"shopId\":999,\"sellerId\":1,\"name\":\"Injected\",\"type\":\"forgery\"}")
                .exchange()
                .expectStatus().isNotFound();
    }
}
