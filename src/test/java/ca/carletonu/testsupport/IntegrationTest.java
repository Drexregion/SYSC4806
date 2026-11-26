package ca.carletonu.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A test that drives the running application over real HTTP.
 * <p>
 * Every test method starts with an empty database, the seeded demo data
 * included, so a class never depends on which classes ran before it and never
 * has to clean up after itself. Tests create the rows they need.
 * <p>
 * Each application context gets a database of its own, so a second context
 * starting up cannot drop the first one's tables. Setting the URL here also
 * means an environment variable cannot point these tests at a real database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:integration-${random.uuid}")
@AutoConfigureRestTestClient
@ExtendWith(EmptyDatabaseExtension.class)
public @interface IntegrationTest {
}
