package ca.carletonu.testsupport;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Lets every event a test published finish before the next test starts.
 * Otherwise a late one would write to the read model after the next test's
 * database had already been emptied.
 */
class DrainKafkaExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext context) {
        SpringExtension.getApplicationContext(context).getBean(KafkaEvents.class).awaitProcessed();
    }
}
