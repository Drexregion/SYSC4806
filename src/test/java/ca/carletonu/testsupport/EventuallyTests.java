package ca.carletonu.testsupport;

import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static ca.carletonu.testsupport.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventuallyTests {

    @Test
    void retriesUntilTheAssertionPasses() {
        AtomicInteger attempts = new AtomicInteger();

        eventually(() -> assertThat(attempts.incrementAndGet()).isEqualTo(3));

        assertThat(attempts).hasValue(3);
    }

    @Test
    void failsWithTheLastErrorWhenTheAssertionNeverPasses() {
        assertThatThrownBy(() -> eventually(Duration.ofMillis(200),
                () -> assertThat("still empty").isEqualTo("projected")))
                .isInstanceOf(ConditionTimeoutException.class)
                .hasMessageContaining("still empty");
    }
}
