package org.chucc.vcserver.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests backward compatibility of BranchMergedEvent schema evolution.
 * Verifies that old events (without conflictsResolved) can be deserialized correctly.
 */
@SpringBootTest
class BranchMergedEventBackwardCompatibilityTest {

  @Autowired
  private ObjectMapper objectMapper;

  /**
   * Critical Test: Old events (without conflictsResolved field) must deserialize correctly.
   * This simulates events already stored in Kafka from Phase 1 production.
   */
  @Test
  void oldEventFormat_withoutConflictsResolved_shouldDeserialize() throws Exception {
    // Simulate old event JSON from Kafka (Phase 1 - before conflictsResolved field)
    String oldEventJson = """
        {
          "eventType": "BranchMerged",
          "eventId": "01932e3f-1234-7abc-9def-123456789abc",
          "dataset": "default",
          "branchName": "main",
          "sourceRef": "refs/heads/feature",
          "commitId": "01932e3f-5678-7abc-9def-abcdef123456",
          "parents": [
            "01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"
          ],
          "message": "Merge feature into main",
          "author": "Alice",
          "timestamp": "2024-01-15T10:30:00Z",
          "rdfPatch": "H\\nTC 0\\nPA prefix ex: <http://example.org/>\\n",
          "patchSize": 5
        }
        """;

    // Should deserialize successfully with new code
    BranchMergedEvent event = objectMapper.readValue(oldEventJson, BranchMergedEvent.class);

    // Verify all original fields are present
    assertThat(event.eventId()).isEqualTo("01932e3f-1234-7abc-9def-123456789abc");
    assertThat(event.dataset()).isEqualTo("default");
    assertThat(event.branchName()).isEqualTo("main");
    assertThat(event.sourceRef()).isEqualTo("refs/heads/feature");
    assertThat(event.commitId()).isEqualTo("01932e3f-5678-7abc-9def-abcdef123456");
    assertThat(event.parents()).containsExactly(
        "01932e3f-0000-7abc-9def-000000000001",
        "01932e3f-0000-7abc-9def-000000000002"
    );
    assertThat(event.message()).isEqualTo("Merge feature into main");
    assertThat(event.author()).isEqualTo("Alice");
    assertThat(event.timestamp()).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    assertThat(event.rdfPatch()).isEqualTo("H\nTC 0\nPA prefix ex: <http://example.org/>\n");
    assertThat(event.patchSize()).isEqualTo(5);

    // NEW FIELD: Should default to null (backward compatible)
    assertThat(event.conflictsResolved()).isNull();
  }

  /**
   * Critical Test: New events (with conflictsResolved field) must deserialize correctly.
   */
  @Test
  void newEventFormat_withConflictsResolved_shouldDeserialize() throws Exception {
    // New event with conflictsResolved field (Phase 2+)
    String newEventJson = """
        {
          "eventType": "BranchMerged",
          "eventId": "01932e3f-1234-7abc-9def-123456789abc",
          "dataset": "default",
          "branchName": "main",
          "sourceRef": "refs/heads/feature",
          "commitId": "01932e3f-5678-7abc-9def-abcdef123456",
          "parents": [
            "01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"
          ],
          "message": "Merge feature into main",
          "author": "Alice",
          "timestamp": "2024-01-15T10:30:00Z",
          "rdfPatch": "H\\nTC 0\\nPA prefix ex: <http://example.org/>\\n",
          "patchSize": 5,
          "conflictsResolved": 3
        }
        """;

    BranchMergedEvent event = objectMapper.readValue(newEventJson, BranchMergedEvent.class);

    // All fields should be present
    assertThat(event.eventId()).isEqualTo("01932e3f-1234-7abc-9def-123456789abc");
    assertThat(event.dataset()).isEqualTo("default");
    assertThat(event.conflictsResolved()).isEqualTo(3);
  }

  /**
   * Critical Test: Serialization should produce JSON with all fields (including null).
   */
  @Test
  void serialization_shouldIncludeAllFields() throws Exception {
    BranchMergedEvent event = new BranchMergedEvent(
        "01932e3f-1234-7abc-9def-123456789abc",
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.parse("2024-01-15T10:30:00Z"),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5,
        null  // conflictsResolved = null for backward compatibility
    );

    String json = objectMapper.writeValueAsString(event);

    // Parse back to verify structure
    com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
    assertThat(node.has("eventType")).isTrue();
    assertThat(node.get("eventType").asText()).isEqualTo("BranchMerged");
    assertThat(node.has("eventId")).isTrue();
    assertThat(node.has("dataset")).isTrue();
    assertThat(node.has("branchName")).isTrue();
    assertThat(node.has("conflictsResolved")).isTrue();
    assertThat(node.get("conflictsResolved").isNull()).isTrue();
  }

  /**
   * Test: Convenience constructor should create event with null conflictsResolved.
   */
  @Test
  void convenienceConstructor_shouldDefaultConflictsResolvedToNull() {
    BranchMergedEvent event = new BranchMergedEvent(
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.parse("2024-01-15T10:30:00Z"),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5
    );

    // Should auto-generate eventId
    assertThat(event.eventId()).isNotNull();

    // Should default conflictsResolved to null
    assertThat(event.conflictsResolved()).isNull();
  }

  /**
   * Test: Validation should allow null conflictsResolved (backward compatibility).
   */
  @Test
  void validation_shouldAllowNullConflictsResolved() {
    // Should NOT throw exception
    BranchMergedEvent event = new BranchMergedEvent(
        null,
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.now(),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5,
        null  // NULL is valid
    );

    assertThat(event.conflictsResolved()).isNull();
  }

  /**
   * Test: Validation should allow zero conflictsResolved.
   */
  @Test
  void validation_shouldAllowZeroConflictsResolved() {
    BranchMergedEvent event = new BranchMergedEvent(
        null,
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.now(),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5,
        0  // ZERO is valid (no conflicts)
    );

    assertThat(event.conflictsResolved()).isEqualTo(0);
  }

  /**
   * Test: Validation should reject negative conflictsResolved.
   */
  @Test
  void validation_shouldRejectNegativeConflictsResolved() {
    assertThatThrownBy(() -> new BranchMergedEvent(
        null,
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.now(),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5,
        -1  // INVALID: negative
    )).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Conflicts resolved cannot be negative");
  }

  /**
   * Critical Test: Round-trip serialization should preserve all fields.
   * This tests the actual Kafka serialization path (concrete type).
   */
  @Test
  void roundTripSerialization_shouldPreserveAllFields() throws Exception {
    BranchMergedEvent original = new BranchMergedEvent(
        "01932e3f-1234-7abc-9def-123456789abc",
        "default",
        "main",
        "refs/heads/feature",
        "01932e3f-5678-7abc-9def-abcdef123456",
        List.of("01932e3f-0000-7abc-9def-000000000001",
            "01932e3f-0000-7abc-9def-000000000002"),
        "Merge feature into main",
        "Alice",
        Instant.parse("2024-01-15T10:30:00Z"),
        "H\nTC 0\nPA prefix ex: <http://example.org/>\n",
        5,
        3  // With conflicts resolved
    );

    // Serialize to JSON (Kafka uses concrete type serialization)
    String json = objectMapper.writeValueAsString(original);

    // Deserialize back from JSON
    BranchMergedEvent deserialized = objectMapper.readValue(json, BranchMergedEvent.class);

    // All fields should match
    assertThat(deserialized.eventId()).isEqualTo(original.eventId());
    assertThat(deserialized.dataset()).isEqualTo(original.dataset());
    assertThat(deserialized.branchName()).isEqualTo(original.branchName());
    assertThat(deserialized.sourceRef()).isEqualTo(original.sourceRef());
    assertThat(deserialized.commitId()).isEqualTo(original.commitId());
    assertThat(deserialized.parents()).isEqualTo(original.parents());
    assertThat(deserialized.message()).isEqualTo(original.message());
    assertThat(deserialized.author()).isEqualTo(original.author());
    assertThat(deserialized.timestamp()).isEqualTo(original.timestamp());
    assertThat(deserialized.rdfPatch()).isEqualTo(original.rdfPatch());
    assertThat(deserialized.patchSize()).isEqualTo(original.patchSize());
    assertThat(deserialized.conflictsResolved()).isEqualTo(original.conflictsResolved());
  }

  /**
   * Critical Test: Mixed events (old + new format) should both deserialize correctly.
   * This simulates event replay from Kafka with mixed event versions.
   */
  @Test
  void mixedEventFormats_shouldBothDeserialize() throws Exception {
    // Old event (no conflictsResolved)
    String oldEventJson = """
        {
          "eventType": "BranchMerged",
          "eventId": "01932e3f-1111-7abc-9def-111111111111",
          "dataset": "default",
          "branchName": "main",
          "sourceRef": "refs/heads/feature-1",
          "commitId": "01932e3f-aaaa-7abc-9def-aaaaaaaaaaaa",
          "parents": ["p1", "p2"],
          "message": "Old merge",
          "author": "Alice",
          "timestamp": "2024-01-01T10:00:00Z",
          "rdfPatch": "H\\n",
          "patchSize": 0
        }
        """;

    // New event (with conflictsResolved)
    String newEventJson = """
        {
          "eventType": "BranchMerged",
          "eventId": "01932e3f-2222-7abc-9def-222222222222",
          "dataset": "default",
          "branchName": "main",
          "sourceRef": "refs/heads/feature-2",
          "commitId": "01932e3f-bbbb-7abc-9def-bbbbbbbbbbbb",
          "parents": ["p3", "p4"],
          "message": "New merge",
          "author": "Bob",
          "timestamp": "2024-02-01T10:00:00Z",
          "rdfPatch": "H\\n",
          "patchSize": 5,
          "conflictsResolved": 2
        }
        """;

    // Both should deserialize successfully
    BranchMergedEvent oldEvent = objectMapper.readValue(oldEventJson, BranchMergedEvent.class);
    BranchMergedEvent newEvent = objectMapper.readValue(newEventJson, BranchMergedEvent.class);

    // Old event: conflictsResolved should be null
    assertThat(oldEvent.eventId()).isEqualTo("01932e3f-1111-7abc-9def-111111111111");
    assertThat(oldEvent.conflictsResolved()).isNull();

    // New event: conflictsResolved should be 2
    assertThat(newEvent.eventId()).isEqualTo("01932e3f-2222-7abc-9def-222222222222");
    assertThat(newEvent.conflictsResolved()).isEqualTo(2);
  }
}
