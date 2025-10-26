package org.chucc.vcserver.command;

import java.util.Optional;
import org.chucc.vcserver.dto.CreateDatasetRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for CreateDatasetCommand validation logic.
 */
class CreateDatasetCommandTest {

  @Test
  void shouldCreateCommandWithoutKafkaConfig() {
    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.of("Test dataset"),
        "alice",
        Optional.empty(),
        null
    );

    // Then
    assertNotNull(command);
    assertEquals("test-dataset", command.dataset());
    assertEquals("alice", command.author());
  }

  @Test
  void shouldCreateCommandWithValidKafkaConfig() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        12,
        (short) 3,
        2592000000L
    );

    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.of("Test dataset"),
        "alice",
        Optional.empty(),
        config
    );

    // Then
    assertNotNull(command);
    assertEquals(config, command.kafkaConfig());
  }

  @Test
  void shouldRejectPartitionCountTooLow() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        0,  // Invalid - must be at least 1
        (short) 3,
        -1L
    );

    // When/Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new CreateDatasetCommand("test-dataset", Optional.empty(), "alice", Optional.empty(), config)
    );
    assertEquals("Partition count must be between 1 and 100, got: 0", exception.getMessage());
  }

  @Test
  void shouldRejectPartitionCountTooHigh() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        200,  // Invalid - exceeds max of 100
        (short) 3,
        -1L
    );

    // When/Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new CreateDatasetCommand("test-dataset", Optional.empty(), "alice", Optional.empty(), config)
    );
    assertEquals("Partition count must be between 1 and 100, got: 200", exception.getMessage());
  }

  @Test
  void shouldRejectReplicationFactorTooLow() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        6,
        (short) 0,  // Invalid - must be at least 1
        -1L
    );

    // When/Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new CreateDatasetCommand("test-dataset", Optional.empty(), "alice", Optional.empty(), config)
    );
    assertEquals("Replication factor must be between 1 and 5, got: 0", exception.getMessage());
  }

  @Test
  void shouldRejectReplicationFactorTooHigh() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        6,
        (short) 10,  // Invalid - exceeds max of 5
        -1L
    );

    // When/Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new CreateDatasetCommand("test-dataset", Optional.empty(), "alice", Optional.empty(), config)
    );
    assertEquals("Replication factor must be between 1 and 5, got: 10", exception.getMessage());
  }

  @Test
  void shouldRejectRetentionTooLow() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        6,
        (short) 3,
        1000L  // Invalid - less than 1 hour (3600000ms)
    );

    // When/Then
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new CreateDatasetCommand("test-dataset", Optional.empty(), "alice", Optional.empty(), config)
    );
    assertEquals(
        "Retention must be at least 1 hour (3600000ms) or -1 for infinite, got: 1000",
        exception.getMessage()
    );
  }

  @Test
  void shouldAcceptInfiniteRetention() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        6,
        (short) 3,
        -1L  // Valid - infinite retention
    );

    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.empty(),
        "alice",
        Optional.empty(),
        config
    );

    // Then
    assertNotNull(command);
    assertEquals(-1L, command.kafkaConfig().retentionMs());
  }

  @Test
  void shouldAcceptMinimumRetention() {
    // Given
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        6,
        (short) 3,
        3600000L  // Valid - exactly 1 hour
    );

    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.empty(),
        "alice",
        Optional.empty(),
        config
    );

    // Then
    assertNotNull(command);
    assertEquals(3600000L, command.kafkaConfig().retentionMs());
  }

  @Test
  void shouldAcceptNullConfigFields() {
    // Given - all fields are null (will use global defaults)
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        null,
        null,
        null
    );

    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.empty(),
        "alice",
        Optional.empty(),
        config
    );

    // Then
    assertNotNull(command);
    assertNotNull(command.kafkaConfig());
  }

  @Test
  void shouldAcceptPartialConfig() {
    // Given - only partitions specified
    CreateDatasetRequest.KafkaTopicConfig config = new CreateDatasetRequest.KafkaTopicConfig(
        12,
        null,  // Will use global default
        null   // Will use global default
    );

    // When
    CreateDatasetCommand command = new CreateDatasetCommand(
        "test-dataset",
        Optional.empty(),
        "alice",
        Optional.empty(),
        config
    );

    // Then
    assertNotNull(command);
    assertEquals(12, command.kafkaConfig().partitions());
  }
}
