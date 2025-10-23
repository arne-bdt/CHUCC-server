# Task: Add Event Serialization Tests

**Status:** Not Started
**Priority:** 🟡 **Medium**
**Estimated Time:** 30-45 minutes
**Dependencies:** 03-add-event-metadata-headers.md

---

## Context

**Gap:** CHUCC Server uses JSON serialization with manual type mappings, but lacks comprehensive tests to catch serialization breaks.

### Current Implementation

[KafkaConfig.java:166-178](src/main/java/org/chucc/vcserver/config/KafkaConfig.java#L166-L178):
```java
// Manual type mappings (could break if class renamed)
configProps.put(JsonDeserializer.TYPE_MAPPINGS,
    "BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent,"
    + "CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent,"
    // ... 12 manual mappings
);
```

### What's Missing

**No comprehensive serialization tests** to catch:
- ❌ Type mapping errors (wrong class name in mapping)
- ❌ Field serialization issues (transient fields, missing @JsonProperty)
- ❌ Deserialization failures (missing constructors, incompatible types)
- ❌ Breaking changes from refactoring (class renames, field changes)

**Current Protection:**
- ✅ Integration tests catch *some* issues (they publish/consume events)
- ⚠️ Not all 12 event types tested in every integration test
- ⚠️ No explicit round-trip serialization validation

---

## Goal

Add comprehensive unit tests to verify all 12 event types serialize/deserialize correctly through JSON.

**Benefits:**
1. ✅ Catches type mapping errors immediately (compilation/test time)
2. ✅ Validates field serialization (all fields preserved)
3. ✅ Fast feedback (~10 seconds vs 2-3 minute integration tests)
4. ✅ Guards against refactoring breaks
5. ✅ No operational complexity (no Schema Registry to deploy)

---

## Design Decisions

### Approach: Unit Tests vs Schema Registry

**Chosen: Comprehensive Unit Tests**

**Why:**
- ✅ Single Java application (producer & consumer deploy together)
- ✅ Events version together (same pom.xml)
- ✅ No external consumers (yet)
- ✅ Zero operational overhead
- ✅ Fast feedback (seconds, not minutes)
- ✅ Easy to maintain

**Schema Registry Alternative:**
- ⚠️ Over-engineered for current architecture
- ⚠️ 4-5 hours to implement vs 30-45 minutes
- ⚠️ Adds operational complexity (extra service to deploy/monitor)
- ⚠️ Requires adapter layer (240+ lines of boilerplate for 12 events)
- ✅ **Defer until external consumers emerge**

### Test Strategy

**Three test classes:**
1. **EventSerializationTest** - Round-trip JSON serialization for all 12 events
2. **EventTypeMappingTest** - Validates type discriminator mappings
3. **EventEvolutionTest** - Tests backward compatibility (optional fields with defaults)

---

## Implementation Plan

### Step 1: Create EventSerializationTest (20 min)

**File:** `src/test/java/org/chucc/vcserver/event/EventSerializationTest.java`

```java
package org.chucc.vcserver.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

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
        Instant.now(),
        "A <http://example.org/s> <http://example.org/p> \"value\" ."
    );

    // Act: Serialize to JSON
    String json = objectMapper.writeValueAsString(original);

    // Act: Deserialize from JSON
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    // Assert: Round-trip preserves all fields
    assertThat(deserialized).isInstanceOf(CommitCreatedEvent.class);
    CommitCreatedEvent result = (CommitCreatedEvent) deserialized;

    assertThat(result.getEventId()).isEqualTo(original.getEventId());
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
    BranchCreatedEvent original = new BranchCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        "01934f8e-5678-7890-abcd-ef1234567890", // startCommitId
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(BranchCreatedEvent.class);
    BranchCreatedEvent result = (BranchCreatedEvent) deserialized;
    assertThat(result.getEventId()).isEqualTo(original.getEventId());
    assertThat(result.dataset()).isEqualTo(original.dataset());
    assertThat(result.branchName()).isEqualTo(original.branchName());
    assertThat(result.startCommitId()).isEqualTo(original.startCommitId());
  }

  @Test
  void tagCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    TagCreatedEvent original = new TagCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "v1.0.0",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(TagCreatedEvent.class);
  }

  @Test
  void branchResetEvent_shouldSerializeAndDeserialize() throws Exception {
    BranchResetEvent original = new BranchResetEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "main",
        "01934f8e-5678-7890-abcd-ef1234567890", // newHeadCommitId
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(BranchResetEvent.class);
  }

  @Test
  void revertCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    RevertCreatedEvent original = new RevertCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // revertCommitId
        "01934f8e-9012-7890-abcd-ef1234567890", // revertedCommitId
        "main",
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(RevertCreatedEvent.class);
  }

  @Test
  void snapshotCreatedEvent_shouldSerializeAndDeserialize() throws Exception {
    SnapshotCreatedEvent original = new SnapshotCreatedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // commitId
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(SnapshotCreatedEvent.class);
  }

  @Test
  void cherryPickedEvent_shouldSerializeAndDeserialize() throws Exception {
    CherryPickedEvent original = new CherryPickedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // newCommitId
        "01934f8e-9012-7890-abcd-ef1234567890", // cherryPickedCommitId
        "main",
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(CherryPickedEvent.class);
  }

  @Test
  void branchRebasedEvent_shouldSerializeAndDeserialize() throws Exception {
    BranchRebasedEvent original = new BranchRebasedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        "01934f8e-5678-7890-abcd-ef1234567890", // newBaseCommitId
        List.of("commit1", "commit2"), // rebasedCommits
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(BranchRebasedEvent.class);
  }

  @Test
  void commitsSquashedEvent_shouldSerializeAndDeserialize() throws Exception {
    CommitsSquashedEvent original = new CommitsSquashedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // squashedCommitId
        List.of("commit1", "commit2", "commit3"), // originalCommits
        "main",
        "Squashed commit message",
        "Alice",
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(CommitsSquashedEvent.class);
  }

  @Test
  void batchGraphsCompletedEvent_shouldSerializeAndDeserialize() throws Exception {
    BatchGraphsCompletedEvent original = new BatchGraphsCompletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "01934f8e-5678-7890-abcd-ef1234567890", // batchId
        5, // graphCount
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(BatchGraphsCompletedEvent.class);
  }

  @Test
  void branchDeletedEvent_shouldSerializeAndDeserialize() throws Exception {
    BranchDeletedEvent original = new BranchDeletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        "feature-branch",
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(BranchDeletedEvent.class);
  }

  @Test
  void datasetDeletedEvent_shouldSerializeAndDeserialize() throws Exception {
    DatasetDeletedEvent original = new DatasetDeletedEvent(
        "01934f8e-1234-7890-abcd-ef1234567890",
        "testDataset",
        Instant.now()
    );

    String json = objectMapper.writeValueAsString(original);
    VersionControlEvent deserialized = objectMapper.readValue(json, VersionControlEvent.class);

    assertThat(deserialized).isInstanceOf(DatasetDeletedEvent.class);
  }

  @Test
  void allEvents_shouldHaveUniqueTypeDiscriminator() throws Exception {
    // Arrange: Create all 12 event types
    List<VersionControlEvent> events = List.of(
        new CommitCreatedEvent(null, "test", "c1", List.of(), "main", "msg", "author",
            Instant.now(), "patch"),
        new BranchCreatedEvent(null, "test", "branch", "c1", Instant.now()),
        new TagCreatedEvent(null, "test", "tag", "c1", Instant.now()),
        new BranchResetEvent(null, "test", "main", "c1", Instant.now()),
        new RevertCreatedEvent(null, "test", "c1", "c2", "main", Instant.now()),
        new SnapshotCreatedEvent(null, "test", "c1", Instant.now()),
        new CherryPickedEvent(null, "test", "c1", "c2", "main", Instant.now()),
        new BranchRebasedEvent(null, "test", "branch", "c1", List.of(), Instant.now()),
        new CommitsSquashedEvent(null, "test", "c1", List.of(), "main", "msg", "author",
            Instant.now()),
        new BatchGraphsCompletedEvent(null, "test", "b1", 5, Instant.now()),
        new BranchDeletedEvent(null, "test", "branch", Instant.now()),
        new DatasetDeletedEvent(null, "test", Instant.now())
    );

    // Act: Serialize each and extract @type discriminator
    List<String> typeDiscriminators = events.stream()
        .map(event -> {
          try {
            String json = objectMapper.writeValueAsString(event);
            return objectMapper.readTree(json).get("@type").asText();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        })
        .toList();

    // Assert: All 12 types have unique discriminators
    assertThat(typeDiscriminators).hasSize(12);
    assertThat(typeDiscriminators).doesNotHaveDuplicates();

    // Assert: Type discriminators match KafkaConfig mappings
    assertThat(typeDiscriminators).contains(
        "CommitCreated", "BranchCreated", "TagCreated", "BranchReset",
        "RevertCreated", "SnapshotCreated", "CherryPicked", "BranchRebased",
        "CommitsSquashed", "BatchGraphsCompleted", "BranchDeleted", "DatasetDeleted"
    );
  }
}
```

---

### Step 2: Create EventTypeMappingTest (10 min)

**File:** `src/test/java/org/chucc/vcserver/event/EventTypeMappingTest.java`

```java
package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that KafkaConfig type mappings are correct.
 * Prevents runtime errors from typos in type mapping strings.
 */
@SpringBootTest
class EventTypeMappingTest {

  @Autowired
  private ConsumerFactory<String, VersionControlEvent> consumerFactory;

  @Test
  void kafkaConsumer_shouldHaveCorrectTypeMappings() {
    // Act: Get consumer config
    Map<String, Object> config = consumerFactory.getConfigurationProperties();
    String typeMappings = (String) config.get("spring.json.type.mapping");

    // Assert: All 12 event types mapped
    assertThat(typeMappings).contains("BranchCreated:org.chucc.vcserver.event.BranchCreatedEvent");
    assertThat(typeMappings).contains("CommitCreated:org.chucc.vcserver.event.CommitCreatedEvent");
    assertThat(typeMappings).contains("TagCreated:org.chucc.vcserver.event.TagCreatedEvent");
    assertThat(typeMappings).contains("BranchReset:org.chucc.vcserver.event.BranchResetEvent");
    assertThat(typeMappings).contains("RevertCreated:org.chucc.vcserver.event.RevertCreatedEvent");
    assertThat(typeMappings).contains("SnapshotCreated:org.chucc.vcserver.event.SnapshotCreatedEvent");
    assertThat(typeMappings).contains("CherryPicked:org.chucc.vcserver.event.CherryPickedEvent");
    assertThat(typeMappings).contains("BranchRebased:org.chucc.vcserver.event.BranchRebasedEvent");
    assertThat(typeMappings).contains("CommitsSquashed:org.chucc.vcserver.event.CommitsSquashedEvent");
    assertThat(typeMappings).contains("BatchGraphsCompleted:org.chucc.vcserver.event.BatchGraphsCompletedEvent");
    assertThat(typeMappings).contains("BranchDeleted:org.chucc.vcserver.event.BranchDeletedEvent");
    assertThat(typeMappings).contains("DatasetDeleted:org.chucc.vcserver.event.DatasetDeletedEvent");
  }

  @Test
  void allEventClasses_shouldExist() {
    // Assert: All classes in type mappings can be loaded
    String[] expectedClasses = {
        "org.chucc.vcserver.event.BranchCreatedEvent",
        "org.chucc.vcserver.event.CommitCreatedEvent",
        "org.chucc.vcserver.event.TagCreatedEvent",
        "org.chucc.vcserver.event.BranchResetEvent",
        "org.chucc.vcserver.event.RevertCreatedEvent",
        "org.chucc.vcserver.event.SnapshotCreatedEvent",
        "org.chucc.vcserver.event.CherryPickedEvent",
        "org.chucc.vcserver.event.BranchRebasedEvent",
        "org.chucc.vcserver.event.CommitsSquashedEvent",
        "org.chucc.vcserver.event.BatchGraphsCompletedEvent",
        "org.chucc.vcserver.event.BranchDeletedEvent",
        "org.chucc.vcserver.event.DatasetDeletedEvent"
    };

    for (String className : expectedClasses) {
      assertThat(className)
          .as("Event class %s should exist", className)
          .satisfies(name -> {
            try {
              Class.forName(name);
            } catch (ClassNotFoundException e) {
              throw new AssertionError("Class not found: " + name, e);
            }
          });
    }
  }
}
```

---

## Success Criteria

- [ ] EventSerializationTest created with 13 test methods
  - [ ] All 12 event types have round-trip serialization test
  - [ ] Type discriminator uniqueness test
- [ ] EventTypeMappingTest created
  - [ ] Validates KafkaConfig type mappings
  - [ ] Validates all event classes exist
- [ ] All tests pass (`mvn -q test -Dtest=Event*Test`)
- [ ] Zero Checkstyle/SpotBugs/PMD violations

---

## Benefits

### Immediate
1. **Catches breaking changes** - Refactoring event classes triggers test failures
2. **Validates type mappings** - Typos in KafkaConfig caught at test time
3. **Fast feedback** - Tests run in ~10 seconds
4. **No operational overhead** - Pure unit tests, no infrastructure

### Long-term
5. **Documents event structure** - Tests serve as examples
6. **Supports refactoring** - Safe to rename fields/classes
7. **Foundation for evolution** - Easy to add backward compatibility tests later

---

## Future Enhancements

### When External Consumers Emerge
- **Revisit Schema Registry** - Protobuf or Avro for polyglot support
- **Add contract tests** - Pact/Spring Cloud Contract
- **Version events explicitly** - `CommitCreatedEventV2` for breaking changes

### Additional Test Coverage (Optional)
- **EventEvolutionTest** - Test adding optional fields with defaults
- **EventValidationTest** - Test validation constraints (@NotNull, @NotBlank)
- **KafkaIntegrationTest** - End-to-end Kafka round-trip (already covered by existing tests)

---

## Notes

**Complexity:** Low
**Time:** 30-45 minutes
**Risk:** None (pure tests, no production code changes)
**Maintenance:** Minimal (add test when adding new event type)

This pragmatic approach provides **90% of the value** with **10% of the complexity** compared to Schema Registry.

**When to revisit Schema Registry:**
- External microservices consume events
- Polyglot architecture (Python/Go consumers)
- Regulatory compliance requires schema versioning
- Event marketplace/API product

Until then: **Keep it simple, test comprehensively.**
