package org.chucc.vcserver.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SnapshotCreatedEvent.
 */
class SnapshotCreatedEventTest {

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  void shouldCreateValidSnapshot() {
    // Given
    String dataset = "test-dataset";
    String commitId = "550e8400-e29b-41d4-a716-446655440000";
    String branchName = "main";
    Instant timestamp = Instant.now();
    String nquads = "<http://example.org/s> <http://example.org/p> "
        + "<http://example.org/o> <http://example.org/g> .";

    // When
    SnapshotCreatedEvent event = new SnapshotCreatedEvent(
        dataset, commitId, branchName, timestamp, nquads);

    // Then
    assertThat(event.dataset()).isEqualTo(dataset);
    assertThat(event.commitId()).isEqualTo(commitId);
    assertThat(event.branchName()).isEqualTo(branchName);
    assertThat(event.timestamp()).isEqualTo(timestamp);
    assertThat(event.nquads()).isEqualTo(nquads);
  }

  @Test
  void shouldRejectNullDataset() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        null, "commit-id", "main", Instant.now(), "nquads"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Dataset cannot be null");
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "  ", "commit-id", "main", Instant.now(), "nquads"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Dataset cannot be blank");
  }

  @Test
  void shouldRejectNullCommitId() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "dataset", null, "main", Instant.now(), "nquads"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Commit ID cannot be null");
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "dataset", "commit-id", null, Instant.now(), "nquads"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Branch name cannot be null");
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "dataset", "commit-id", "  ", Instant.now(), "nquads"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Branch name cannot be blank");
  }

  @Test
  void shouldRejectNullTimestamp() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "dataset", "commit-id", "main", null, "nquads"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Timestamp cannot be null");
  }

  @Test
  void shouldRejectNullNquads() {
    assertThatThrownBy(() -> new SnapshotCreatedEvent(
        "dataset", "commit-id", "main", Instant.now(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("N-Quads cannot be null");
  }

  @Test
  void shouldSerializeToJson() throws Exception {
    // Given
    SnapshotCreatedEvent event = new SnapshotCreatedEvent(
        "test-dataset",
        "550e8400-e29b-41d4-a716-446655440000",
        "main",
        Instant.parse("2024-01-15T10:30:00Z"),
        "<http://example.org/s> <http://example.org/p> <http://example.org/o> ."
    );

    // When
    String json = objectMapper.writeValueAsString(event);

    // Then
    assertThat(json).contains("\"dataset\":\"test-dataset\"");
    assertThat(json).contains("\"commitId\":\"550e8400-e29b-41d4-a716-446655440000\"");
    assertThat(json).contains("\"branchName\":\"main\"");
    assertThat(json).contains("\"timestamp\"");
    assertThat(json).contains("1705314600"); // Unix timestamp for 2024-01-15T10:30:00Z
  }

  @Test
  void shouldDeserializeFromJson() throws Exception {
    // Given
    String json = """
        {
          "eventType": "SnapshotCreated",
          "dataset": "test-dataset",
          "commitId": "550e8400-e29b-41d4-a716-446655440000",
          "branchName": "main",
          "timestamp": 1705314600.000000000,
          "nquads": "<http://example.org/s> <http://example.org/p> <http://example.org/o> ."
        }
        """;

    // When
    SnapshotCreatedEvent event = objectMapper.readValue(json, SnapshotCreatedEvent.class);

    // Then
    assertThat(event.dataset()).isEqualTo("test-dataset");
    assertThat(event.commitId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
    assertThat(event.branchName()).isEqualTo("main");
    assertThat(event.timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    assertThat(event.nquads()).contains("http://example.org/s");
  }
}
