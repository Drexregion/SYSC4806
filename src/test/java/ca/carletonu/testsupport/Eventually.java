package ca.carletonu.testsupport;

import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;

import java.time.Duration;

/**
 * Asserts on state that another thread updates, such as a read model.
 */
public final class Eventually {

    /** Generous, because a test only waits this long when it is going to fail. */
    static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private Eventually() {
    }

    /**
     * Repeats the assertions until they pass. If they never do, fails with the
     * last assertion error.
     * <p>
     * Read the state inside the lambda, so each attempt sees fresh data:
     * <pre>{@code
     * eventually(() -> assertThat(repository.findByShopId(id)).isPresent());
     * }</pre>
     * Do not use this to check that something is absent, because that passes
     * on the first attempt. See {@link KafkaEvents#awaitProcessed()}.
     */
    public static void eventually(ThrowingRunnable assertions) {
        eventually(TIMEOUT, assertions);
    }

    static void eventually(Duration timeout, ThrowingRunnable assertions) {
        Awaitility.await()
                .atMost(timeout)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(assertions);
    }
}
