package ca.carletonu.controllers;

import ca.carletonu.models.Customer;
import ca.carletonu.models.Person;
import ca.carletonu.models.Seller;
import ca.carletonu.models.repositories.CustomerRepository;
import ca.carletonu.models.repositories.PersonRepository;
import ca.carletonu.models.repositories.SellerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the account page (#3), driven over real HTTP against
 * the running application.
 *
 * Each test saves its own users with distinctive names, so seed data or rows
 * left by other tests in the shared in-memory database cannot affect them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AccountControllerTests {

    @Autowired
    private RestTestClient client;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void sellerAccountShowsDetailsWithoutAddressOrPassword() {
        Seller seller = sellerRepository.save(new Seller("Rotimi Ajayi", "aj@example.com", "bankai", "613-555-0101"));

        client.get().uri("/accounts/{id}", seller.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("Rotimi Ajayi")
                        .contains("aj@example.com")
                        .contains("613-555-0101")
                        .doesNotContain("name=\"address\"")
                        .doesNotContain("bankai"));
    }

    @Test
    void customerAccountShowsAddress() {
        Customer customer = customerRepository.save(
                new Customer("Idara Udoh", "idara@example.com", "messi", "613-555-0102", "Barcelona Street"));

        client.get().uri("/accounts/{id}", customer.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("Idara Udoh")
                        .contains("Barcelona Street"));
    }

    @Test
    void unknownAccountIsNotFound() {
        client.get().uri("/accounts/{id}", Long.MAX_VALUE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateChangesDetailsAndKeepsPassword() {
        Seller seller = sellerRepository.save(new Seller("Divine Eyo", "d@example.com", "speq", "111"));

        postUpdate(seller.getId(), "Divine Jojo", "bigD@example.com", "222", "moon");

        Person updated = personRepository.findById(seller.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Divine Jojo");
        assertThat(updated.getEmail()).isEqualTo("bigD@example.com");
        assertThat(updated.getPhone()).isEqualTo("222");
        assertThat(updated.getPassword()).isEqualTo("speq");
    }

    @Test
    void updateStoresCustomerAddress() {
        Customer customer = customerRepository.save(
                new Customer("Subomi", "fineBoy@example.com", "liverpool", "333", "Old Ham"));

        postUpdate(customer.getId(), "Subomi", "fineBoy@example.com", "333", "New Ham");

        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getAddress())
                .isEqualTo("New Ham");
    }

    @Test
    void blankNameIsRejected() {
        Seller seller = sellerRepository.save(new Seller("Iheanyi", "ino@example.com", "guitarist", "444"));

        postUpdate(seller.getId(), "  ", "changed@example.com", "555", null);

        Person unchanged = personRepository.findById(seller.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Iheanyi");
        assertThat(unchanged.getEmail()).isEqualTo("ino@example.com");
    }

    private void postUpdate(Long id, String name, String email, String phone, String address) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", name);
        form.add("email", email);
        form.add("phone", phone);
        if (address != null) {
            form.add("address", address);
        }

        client.post().uri("/accounts/{id}", id)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .exchange()
                .expectStatus().is3xxRedirection()
                // Tomcat makes the redirect absolute, so match the path at the end
                .expectHeader().valueMatches("Location", ".*/accounts/" + id + "$");
    }
}
