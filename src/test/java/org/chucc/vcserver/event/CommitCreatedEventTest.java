package org.chucc.vcserver.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommitCreatedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    List<String> parents = List.of("parent-1", "parent-2");
    CommitCreatedEvent event = new CommitCreatedEvent(
        "test-dataset",
        "commit-id",
        parents,
        "Initial commit",
        "Alice <alice@example.com>",
        now,
        "H 1 .\n"
    );

    assertNotNull(event);
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
        "dataset",
        "commit-id",
        List.of(),
        "Initial commit",
        "author",
        Instant.now(),
        "patch"
    );

    assertEquals(0, event.parents().size());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent(null, "id", List.of(), "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("", "id", List.of(), "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullParentsThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("dataset", "id", null, "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("dataset", "id", List.of(), null, "author", Instant.now(), "patch")
    );
  }

  @Test
  void testBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("dataset", "id", List.of(), "  ", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("dataset", "id", List.of(), "msg", null, Instant.now(), "patch")
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CommitCreatedEvent("dataset", "id", List.of(), "msg", "", Instant.now(), "patch")
    );
  }

  @Test
  void testNullRdfPatchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CommitCreatedEvent("dataset", "id", List.of(), "msg", "author", Instant.now(), null)
    );
  }

  @Test
  void testParentsListIsImmutable() {
    List<String> parents = new java.util.ArrayList<>();
    parents.add("parent-1");

    CommitCreatedEvent event = new CommitCreatedEvent(
        "dataset", "id", parents, "msg", "author", Instant.now(), "patch");

    // Modify original list
    parents.add("parent-2");

    // Event should still have only one parent
    assertEquals(1, event.parents().size());
  }
}
