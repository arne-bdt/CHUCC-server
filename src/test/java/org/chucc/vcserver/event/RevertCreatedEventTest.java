package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RevertCreatedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    RevertCreatedEvent event = new RevertCreatedEvent(
        "test-dataset",
        "new-commit-id",
        "reverted-commit-id",
        "main",
        "Revert 'bad changes'",
        "Alice <alice@example.com>",
        now,
        "H 1 .\n"
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("new-commit-id", event.revertCommitId());
    assertEquals("reverted-commit-id", event.revertedCommitId());
    assertEquals("Revert 'bad changes'", event.message());
    assertEquals("Alice <alice@example.com>", event.author());
    assertEquals(now, event.timestamp());
    assertEquals("H 1 .\n", event.rdfPatch());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent(null, "new", "old", "main", "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("", "new", "old", "main", "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullRevertCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", null, "old", "main", "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullRevertedCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", null, "main", "msg", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", null, "author", Instant.now(), "patch")
    );
  }

  @Test
  void testBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "  ", "author", Instant.now(), "patch")
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", null, Instant.now(), "patch")
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", "", Instant.now(), "patch")
    );
  }

  @Test
  void testNullRdfPatchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", "author", Instant.now(), null)
    );
  }
}
