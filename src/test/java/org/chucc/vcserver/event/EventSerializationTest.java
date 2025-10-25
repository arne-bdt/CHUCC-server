package org.chucc.vcserver.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests JSON serialization/deserialization for all event types.
 * Catches type mapping errors and serialization issues.
 */
@SpringBootTest
class EventSerializationTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void commitCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    CommitCreatedEvent original = new CommitCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890", // eventId
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        List.of("parent1", "parent2"),
        "main",
        "Test commit",
        "Alice",
        Instant.parse("2025-01-15T10:00:00Z"),
        "A <http://example.org/s> <http://example.org/p> \"value\" ."
        , 1
    );

    // Act: Serialize to JSON
    String json = objectMapper.writeValueAsString(original);

    // Act: Deserialize from JSON
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert: Round-trip preserves all fields
    assertThat(deserialized).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent result = (CommitCreatedEvent) deserialized;

    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.commitId()).isEqualTo(original.commitId());
    assertThat(result.parents()).isEqualTo(original.parents());
    assertThat(result.branch()).isEqualTo(original.branch());
    assertThat(result.message()).isEqualTo(original.message());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.rdfPatch()).isEqualTo(original.rdfPatch());
  }

  @Test
  void branchCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    BranchCreatedEvent original = new BranchCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        "main", // sourceRef
        false, // isProtected
        "test-author", // author
        Instant.parse("2025-01-15T10:00:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(BranchCreatedEvent.class);
    BranchCreatedEvent result = (BranchCreatedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branchName()).isEqualTo(original.branchName());
    assertThat(result.commitId()).isEqualTo(original.commitId());
    assertThat(result.sourceRef()).isEqualTo(original.sourceRef());
    assertThat(result.isProtected()).isEqualTo(original.isProtected());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void tagCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    TagCreatedEvent original = new TagCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "v1.0.0",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        Instant.parse("2025-01-15T10:00:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(TagCreatedEvent.class);
    TagCreatedEvent result = (TagCreatedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.tagName()).isEqualTo(original.tagName());
    assertThat(result.commitId()).isEqualTo(original.commitId());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void branchResetEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    BranchResetEvent original = new BranchResetEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "main",
        "01934f8e-5678-7890-abcd-ef1234567890", // fromCommitId
        "01934f8e-9012-7890-abcd-ef1234567890", // toCommitId
        Instant.parse("2025-01-15T10:00:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(BranchResetEvent.class);
    BranchResetEvent result = (BranchResetEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branchName()).isEqualTo(original.branchName());
    assertThat(result.fromCommitId()).isEqualTo(original.fromCommitId());
    assertThat(result.toCommitId()).isEqualTo(original.toCommitId());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void revertCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    RevertCreatedEvent original = new RevertCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // revertCommitId
        "01934f8e-9012-7890-abcd-ef1234567890", // revertedCommitId
        "main",
        "Revert commit message",
        "Bob",
        Instant.parse("2025-01-15T10:00:00Z"),
        "D <http://example.org/s> <http://example.org/p> \"value\" .",
        1
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(RevertCreatedEvent.class);
    RevertCreatedEvent result = (RevertCreatedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.revertCommitId()).isEqualTo(original.revertCommitId());
    assertThat(result.revertedCommitId()).isEqualTo(original.revertedCommitId());
    assertThat(result.branch()).isEqualTo(original.branch());
    assertThat(result.message()).isEqualTo(original.message());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.rdfPatch()).isEqualTo(original.rdfPatch());
  }

  @Test
  void snapshotCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    SnapshotCreatedEvent original = new SnapshotCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        "main",
        Instant.parse("2025-01-15T10:00:00Z"),
        "<http://example.org/s> <http://example.org/p> \"value\" <http://example.org/g> ."
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(SnapshotCreatedEvent.class);
    SnapshotCreatedEvent result = (SnapshotCreatedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.commitId()).isEqualTo(original.commitId());
    assertThat(result.branchName()).isEqualTo(original.branchName());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.nquads()).isEqualTo(original.nquads());
  }

  @Test
  void cherryPickedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    CherryPickedEvent original = new CherryPickedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // newCommitId
        "01934f8e-9012-7890-abcd-ef1234567890", // sourceCommitId
        "main",
        "Cherry-picked commit",
        "Charlie",
        Instant.parse("2025-01-15T10:00:00Z"),
        "A <http://example.org/s> <http://example.org/p> \"cherry\" .",
        1
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(CherryPickedEvent.class);
    CherryPickedEvent result = (CherryPickedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.newCommitId()).isEqualTo(original.newCommitId());
    assertThat(result.sourceCommitId()).isEqualTo(original.sourceCommitId());
    assertThat(result.branch()).isEqualTo(original.branch());
    assertThat(result.message()).isEqualTo(original.message());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.rdfPatch()).isEqualTo(original.rdfPatch());
  }

  @Test
  void branchRebasedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    BranchRebasedEvent original = new BranchRebasedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        "01934f8e-5678-7890-abcd-ef1234567890", // newHead
        "01934f8e-9012-7890-abcd-ef1234567890", // previousHead
        List.of("commit1", "commit2"), // newCommits
        "Dave",
        Instant.parse("2025-01-15T10:00:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(BranchRebasedEvent.class);
    BranchRebasedEvent result = (BranchRebasedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branch()).isEqualTo(original.branch());
    assertThat(result.newHead()).isEqualTo(original.newHead());
    assertThat(result.previousHead()).isEqualTo(original.previousHead());
    assertThat(result.newCommits()).isEqualTo(original.newCommits());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void commitsSquashedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    CommitsSquashedEvent original = new CommitsSquashedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "main",
        "01934f8e-5678-7890-abcd-ef1234567890", // newCommitId
        List.of("commit1", "commit2", "commit3"), // squashedCommitIds
        "Eve",
        "Squashed commit message",
        Instant.parse("2025-01-15T10:00:00Z"),
        "01934f8e-9012-7890-abcd-ef1234567890" // previousHead
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(CommitsSquashedEvent.class);
    CommitsSquashedEvent result = (CommitsSquashedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branch()).isEqualTo(original.branch());
    assertThat(result.newCommitId()).isEqualTo(original.newCommitId());
    assertThat(result.squashedCommitIds()).isEqualTo(original.squashedCommitIds());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.message()).isEqualTo(original.message());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.previousHead()).isEqualTo(original.previousHead());
  }

  @Test
  void batchGraphsCompletedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    CommitCreatedEvent commit1 = new CommitCreatedEvent(
        "testDataset",
        "c1",
        List.of(),
        "main",
        "msg1",
        "Alice",
        Instant.parse("2025-01-15T10:00:00Z"),
        "patch1"
        , 1
    );
    CommitCreatedEvent commit2 = new CommitCreatedEvent(
        "testDataset",
        "c2",
        List.of("c1"),
        "main",
        "msg2",
        "Alice",
        Instant.parse("2025-01-15T10:01:00Z"),
        "patch2"
        , 1
    );

    BatchGraphsCompletedEvent original = new BatchGraphsCompletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        List.of(commit1, commit2),
        Instant.parse("2025-01-15T10:02:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(BatchGraphsCompletedEvent.class);
    BatchGraphsCompletedEvent result = (BatchGraphsCompletedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.commits()).hasSize(2);
    assertThat(result.commits().get(0).commitId()).isEqualTo("c1");
    assertThat(result.commits().get(1).commitId()).isEqualTo("c2");
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void branchDeletedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    BranchDeletedEvent original = new BranchDeletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        "01934f8e-5678-7890-abcd-ef1234567890", // lastCommitId
        "Frank",
        Instant.parse("2025-01-15T10:00:00Z")
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(BranchDeletedEvent.class);
    BranchDeletedEvent result = (BranchDeletedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branchName()).isEqualTo(original.branchName());
    assertThat(result.lastCommitId()).isEqualTo(original.lastCommitId());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
  }

  @Test
  void datasetDeletedEvent_shouldSerializeAndDeserialize() throws Exception {
    // Arrange
    DatasetDeletedEvent original = new DatasetDeletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "Grace",
        Instant.parse("2025-01-15T10:00:00Z"),
        List.of("main", "feature-1", "feature-2"),
        42,
        true
    );

    // Act
    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert
    assertThat(deserialized).isInstanceOf(DatasetDeletedEvent.class);
    DatasetDeletedEvent result = (DatasetDeletedEvent) deserialized;
    assertThat(result.eventId()).isEqualTo(original.eventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.author()).isEqualTo(original.author());
    assertThat(result.timestamp()).isEqualTo(original.timestamp());
    assertThat(result.deletedBranches()).isEqualTo(original.deletedBranches());
    assertThat(result.deletedCommitCount()).isEqualTo(original.deletedCommitCount());
    assertThat(result.kafkaTopicDeleted()).isEqualTo(original.kafkaTopicDeleted());
  }

  @Test
  void allEvents_shouldHaveUniqueTypeDiscriminator() throws Exception {
    // Arrange: Create all 12 event types
    List<VersionControlEvent> events = List.of(
        new CommitCreatedEvent(null, "test", "c1", List.of(), "main", "msg", "author",
            Instant.now(), "patch", 1),
        new BranchCreatedEvent(null, "test", "branch", "c1", "main", false, "author",
            Instant.now()),
        new TagCreatedEvent(null, "test", "tag", "c1", Instant.now()),
        new BranchResetEvent(null, "test", "main", "c1", "c2", Instant.now()),
        new RevertCreatedEvent(null, "test", "c1", "c2", "main", "msg", "author", Instant.now(),
            "patch", 1),
        new SnapshotCreatedEvent(null, "test", "c1", "main", Instant.now(), "nquads"),
        new CherryPickedEvent(null, "test", "c1", "c2", "main", "msg", "author", Instant.now(),
            "patch", 1),
        new BranchRebasedEvent(null, "test", "branch", "c1", "c2", List.of(), "author",
            Instant.now()),
        new CommitsSquashedEvent(null, "test", "main", "c1", List.of(), "author", "msg",
            Instant.now(), "c2"),
        new BatchGraphsCompletedEvent(null, "test", List.of(), Instant.now()),
        new BranchDeletedEvent(null, "test", "branch", "c1", "author", Instant.now()),
        new DatasetDeletedEvent(null, "test", "author", Instant.now(), List.of(), 0, false)
    );

    // Act: Serialize each and extract eventType discriminator
    List<String> typeDiscriminators = events.stream()
        .map(event -> {
          try {
            String json = objectMapper.writeValueAsString(event);
            return objectMapper.readTree(json).get("eventType").asText();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .toList();

    // Assert: All 12 types have unique discriminators
    assertThat(typeDiscriminators).hasSize(12);
    assertThat(typeDiscriminators).doesNotHaveDuplicates();

    // Assert: Type discriminators match VersionControlEvent @JsonSubTypes
    assertThat(typeDiscriminators).contains(
        "CommitCreated", "BranchCreated", "TagCreated", "BranchReset",
        "RevertCreated", "SnapshotCreated", "CherryPicked", "BranchRebased",
        "CommitsSquashed", "BatchGraphsCompleted", "BranchDeleted", "DatasetDeleted"
    );
  }
}
