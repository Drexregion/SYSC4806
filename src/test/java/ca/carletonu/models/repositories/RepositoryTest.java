package ca.carletonu.models.repositories;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this instead of a bare {@code @DataJpaTest} on repository tests.
 * <p>
 * By default {@code @DataJpaTest} swaps in its own embedded H2 database, and
 * inserting any subclass entity (Seller, Customer, Item, Service) there fails
 * with "Check constraint invalid" on Hibernate's dtype check. Keeping the
 * normal H2 setup, with a database name of its own so it never shares tables
 * with the {@code @SpringBootTest} classes, avoids that.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:repository-tests")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public @interface RepositoryTest {
}
