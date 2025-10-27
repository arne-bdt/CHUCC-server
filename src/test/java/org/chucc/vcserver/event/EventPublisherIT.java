package org.chucc.vcserver.event;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for EventPublisher using embedded Kafka.
 */
@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
    partitions = 3,
    topics = {"vc.test-dataset.events"},
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9093",
        "port=9093"
    })
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "kafka.transactional-id-prefix="
})
class EventPublisherIT {

  @Autowired
  private EventPublisher eventPublisher;

  @Autowired
  private EmbeddedKafkaBroker embeddedKafka;

  private KafkaMessageListenerContainer<String, String> container;
  private ConsumerRecord<String, String> receivedRecord;

  @BeforeEach
  void setUp() {
    // Set up consumer to verify published events
    Map<String, Object> consumerProps = Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        embeddedKafka.getBrokersAsString(),
        ConsumerConfig.GROUP_ID_CONFIG, "test-group",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
    );

    DefaultKafkaConsumerFactory<String, String> consumerFactory =
        new DefaultKafkaConsumerFactory<>(consumerProps);

    ContainerProperties containerProperties =
        new ContainerProperties("vc.test-dataset.events");
    container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);

    container.setupMessageListener((MessageListener<String, String>) record -> {
      receivedRecord = record;
    });

    container.start();
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
  }

  @AfterEach
  void tearDown() {
    if (container != null) {
      container.stop();
    }
  }

  @Test
  void testPublishBranchCreatedEvent() throws Exception {
    // Given
    BranchCreatedEvent event = new BranchCreatedEvent(
        "test-dataset",
        "main",
        "550e8400-e29b-41d4-a716-446655440000",
        "main",
        false,
        "test-author",
        Instant.now()
    );

    // When
    eventPublisher.publish(event).get(10, TimeUnit.SECONDS);

    // Then - Wait for message to be consumed
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          assertNotNull(receivedRecord, "Should receive the published event");
        });
    assertEquals("test-dataset:main", receivedRecord.key());

    // Verify headers
    assertHeaderExists(receivedRecord, EventHeaders.DATASET, "test-dataset");
    assertHeaderExists(receivedRecord, EventHeaders.EVENT_TYPE, "BranchCreated");
    assertHeaderExists(receivedRecord, EventHeaders.BRANCH, "main");
    assertHeaderExists(receivedRecord, EventHeaders.COMMIT_ID,
        "550e8400-e29b-41d4-a716-446655440000");

    // Verify JSON payload
    String json = receivedRecord.value();
    assertTrue(json.contains("\"eventType\":\"BranchCreated\""));
    assertTrue(json.contains("\"branchName\":\"main\""));
    assertTrue(json.contains("\"dataset\":\"test-dataset\""));
  }

  @Test
  void testPublishCommitCreatedEvent() throws Exception {
    // Given
    CommitCreatedEvent event = new CommitCreatedEvent(
        "test-dataset",
        "commit-123",
        List.of("parent-1", "parent-2"),
        null,
        "Test commit message",
        "Alice <alice@example.com>",
        Instant.now(),
        "H 1 .\nA <http://example.org/s> <http://example.org/p> <http://example.org/o> .\n",
        1
    );

    // When
    eventPublisher.publish(event).get(10, TimeUnit.SECONDS);

    // Then
    Thread.sleep(1000);
    assertNotNull(receivedRecord);
    assertEquals("test-dataset:commit-123", receivedRecord.key());

    // Verify headers include content type for RDF Patch
    assertHeaderExists(receivedRecord, EventHeaders.CONTENT_TYPE,
        "text/rdf-patch; charset=utf-8");
    assertHeaderExists(receivedRecord, EventHeaders.COMMIT_ID, "commit-123");

    // Verify JSON payload
    String json = receivedRecord.value();
    assertTrue(json.contains("\"eventType\":\"CommitCreated\""));
    assertTrue(json.contains("\"commitId\":\"commit-123\""));
    assertTrue(json.contains("\"message\":\"Test commit message\""));
    assertTrue(json.contains("\"author\":\"Alice <alice@example.com>\""));
  }

  @Test
  void testPublishTagCreatedEvent() throws Exception {
    // Given
    TagCreatedEvent event = new TagCreatedEvent(
        "test-dataset",
        "v1.0.0",
        "commit-456",
        "Release v1.0.0",
        "Charlie",
        Instant.now()
    );

    // When
    eventPublisher.publish(event).get(10, TimeUnit.SECONDS);

    // Then
    Thread.sleep(1000);
    assertNotNull(receivedRecord);

    assertHeaderExists(receivedRecord, EventHeaders.EVENT_TYPE, "TagCreated");
    assertHeaderExists(receivedRecord, EventHeaders.COMMIT_ID, "commit-456");

    String json = receivedRecord.value();
    assertTrue(json.contains("\"eventType\":\"TagCreated\""));
    assertTrue(json.contains("\"tagName\":\"v1.0.0\""));
  }

  private void assertHeaderExists(
      ConsumerRecord<String, String> record,
      String headerKey,
      String expectedValue) {
    org.apache.kafka.common.header.Header header = record.headers().lastHeader(headerKey);
    assertNotNull(header, "Header " + headerKey + " should exist");
    assertEquals(expectedValue, new String(header.value(), StandardCharsets.UTF_8));
  }
}
