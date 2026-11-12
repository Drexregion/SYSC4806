package ca.carletonu.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics the application owns.
 *
 * Only exists under the "kafka" profile. This matters for startup: Spring's
 * KafkaAdmin tries to create every NewTopic bean when the context comes up,
 * so leaving this on without a broker would make the application fail to
 * start, in CI and on Azure alike.
 */
@Configuration
@Profile("kafka")
public class KafkaTopicConfig {

    @Bean
    public NewTopic shopCreatedTopic() {
        return TopicBuilder.name("shop-created")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
