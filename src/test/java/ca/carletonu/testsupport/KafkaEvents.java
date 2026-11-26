package ca.carletonu.testsupport;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.carletonu.testsupport.Eventually.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Knows when the listeners have dealt with everything that was published.
 */
public class KafkaEvents {

    private final KafkaTemplate<?, ?> kafkaTemplate;
    private final KafkaAdmin kafkaAdmin;
    private final KafkaListenerEndpointRegistry listeners;

    KafkaEvents(KafkaTemplate<?, ?> kafkaTemplate, KafkaAdmin kafkaAdmin, KafkaListenerEndpointRegistry listeners) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaAdmin = kafkaAdmin;
        this.listeners = listeners;
    }

    /**
     * Waits until every listener has processed every event published so far.
     * <p>
     * A listener commits its offset only after it has handled the event, so
     * once each committed offset reaches the end of its topic, nothing is left
     * in flight.
     */
    public void awaitProcessed() {
        // Sending is asynchronous too, so make sure the events have reached the broker
        kafkaTemplate.flush();

        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            eventually(() -> {
                for (MessageListenerContainer listener : listeners.getListenerContainers()) {
                    assertCaughtUp(admin, listener);
                }
            });
        }
    }

    private void assertCaughtUp(AdminClient admin, MessageListenerContainer listener) throws Exception {
        Collection<TopicPartition> partitions = listener.getAssignedPartitions();
        assertThat(partitions)
                .as("partitions assigned to %s", listener.getListenerId())
                .isNotEmpty();

        Map<TopicPartition, OffsetAndMetadata> committed = admin
                .listConsumerGroupOffsets(listener.getGroupId())
                .partitionsToOffsetAndMetadata()
                .get();

        var ends = admin
                .listOffsets(partitions.stream()
                        .collect(Collectors.toMap(Function.identity(), partition -> OffsetSpec.latest())))
                .all()
                .get();

        ends.forEach((partition, end) -> {
            OffsetAndMetadata offset = committed.get(partition);
            assertThat(offset == null ? 0 : offset.offset())
                    .as("events on %s processed by group %s", partition, listener.getGroupId())
                    .isEqualTo(end.offset());
        });
    }
}
