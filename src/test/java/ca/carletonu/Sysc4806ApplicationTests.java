package ca.carletonu;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: starts the whole application context. It fails if a bean cannot
 * be created, a property is malformed, or the datasource will not come up, so
 * CI catches a broken configuration before anything is deployed.
 */
@SpringBootTest
class Sysc4806ApplicationTests {

    @Test
    void contextLoads() {
        // Passing means the application context started cleanly.
    }
}
