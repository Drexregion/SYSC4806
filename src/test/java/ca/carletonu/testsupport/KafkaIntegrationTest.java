package ca.carletonu.testsupport;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An {@link IntegrationTest} with the "kafka" profile on and a real broker
 * running inside the test JVM, so events travel the whole asynchronous path:
 * producer, topic, listener, projection.
 * <p>
 * The read model is updated on another thread, so never assert on it straight
 * after a write. Use {@link Eventually#eventually} to wait for something to
 * appear, and {@link KafkaEvents#awaitProcessed()} before asserting that
 * something did not.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@IntegrationTest
@ActiveProfiles("kafka")
@EmbeddedKafka(partitions = 1, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(KafkaEvents.class)
@ExtendWith(DrainKafkaExtension.class)
public @interface KafkaIntegrationTest {
}
