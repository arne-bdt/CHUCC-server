package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitCreatedEventTest {

  @Test
  void testConvenienceConstructorGeneratesEventId() {
    Instant now = Instant.now();
    List<String> parents = List.of("parent-1", "parent-2");
    CommitCreatedEvent event = new CommitCreatedEvent(
        "test-dataset",
        "commit-id",
        parents,
        "main",
        "Initial commit",
        "Alice <alice@example.com>",
        now,
        "H 1 .\n",
        1
    );

    assertNotNull(event);
    assertNotNull(event.eventId());
    assertFalse(event.eventId().isBlank());
    assertEquals("test-dataset", event.dataset());
    assertEquals("commit-id", event.commitId());
    assertEquals(2, event.parents().size());
    assertEquals("Initial commit", event.message());
    assertEquals("Alice <alice@example.com>", event.author());
    assertEquals(now, event.timestamp());
    assertEquals("H 1 .\n", event.rdfPatch());
  }

  @Test
  void testConvenienceConstructorGeneratesUniqueEventIds() {
    CommitCreatedEvent event1 = new CommitCreatedEvent(
        "dataset", "commit-1", List.of(), "main", "msg", "author", Instant.now(), "patch", 1);
    CommitCreatedEvent event2 = new CommitCreatedEvent(
        "dataset", "commit-2", List.of(), "main", "msg", "author", Instant.now(), "patch", 1);

    assertNotEquals(event1.eventId(), event2.eventId());
  }

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    List<String> parents = List.of("parent-1", "parent-2");
    CommitCreatedEvent event = new CommitCreatedEvent(
        "event-id-123",
        "test-dataset",
        "commit-id",
        parents,
        "main",
        "Initial commit",
        "Alice <alice@example.com>",
        now,
        "H 1 .\n",
        1
    );

    assertNotNull(event);
    assertEquals("event-id-123", event.eventId());
    assertEquals("test-dataset", event.dataset());
    assertEquals("commit-id", event.commitId());
    assertEquals(2, event.parents().size());
    assertEquals("Initial commit", event.message());
    assertEquals("Alice <alice@example.com>", event.author());
    assertEquals(now, event.timestamp());
    assertEquals("H 1 .\n", event.rdfPatch());
  }

  @Test
  void testCreateEventWithEmptyParents() {
    CommitCreatedEvent event = new CommitCreatedEvent(
        "event-id",
        "dataset",
        "commit-id",
        List.of(),
        "main",
        "Initial commit",
        "author",
        Instant.now(),
        "patch",
        1
    );

    assertEquals(0, event.parents().size());
  }

  @Test
  void testNullEventIdAutoGenerates() {
    // When eventId is null, it should auto-generate
    CommitCreatedEvent event = new CommitCreatedEvent(
        null, "dataset", "id", List.of(), "main", "msg", "author", Instant.now(), "patch", 1);

    assertNotNull(event.eventId());
    assertFalse(event.eventId().isBlank());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("event-id", null, "id", List.of(), "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("event-id", "", "id", List.of(), "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullParentsThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", null, "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", List.of(), "main", null, "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", List.of(), "main", "  ", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", List.of(), "main", "msg", null, Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", List.of(), "main", "msg", "", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullRdfPatchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("event-id", "dataset", "id", List.of(), "main", "msg", "author", Instant.now(), null, 1)
    );
  }

  @Test
  void testParentsListIsImmutable() {
    List<String> parents = new java.util.ArrayList<>();
    parents.add("parent-1");

    CommitCreatedEvent event = new CommitCreatedEvent(
        "event-id", "dataset", "id", parents, "main", "msg", "author", Instant.now(), "patch", 1);

    // Modify original list
    parents.add("parent-2");

    // Event should still have only one parent
    assertEquals(1, event.parents().size());
  }
}
