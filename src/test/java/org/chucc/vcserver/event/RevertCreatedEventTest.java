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
        "H 1 .\n",
        5
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("new-commit-id", event.revertCommitId());
    assertEquals("reverted-commit-id", event.revertedCommitId());
    assertEquals("Revert 'bad changes'", event.message());
    assertEquals("Alice <alice@example.com>", event.author());
    assertEquals(now, event.timestamp());
    assertEquals("H 1 .\n", event.rdfPatch());
    assertEquals(5, event.patchSize());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent(null, "new", "old", "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("", "new", "old", "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullRevertCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", null, "old", "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullRevertedCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", null, "main", "msg", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", null, "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "  ", "author", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", null, Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", "", Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullRdfPatchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", "author", Instant.now(), null, 1)
    );
  }

  @Test
  void testNegativePatchSizeThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCreatedEvent("dataset", "new", "old", "main", "msg", "author", Instant.now(), "patch", -1)
    );
  }

  @Test
  void testZeroPatchSizeIsValid() {
    RevertCreatedEvent event = new RevertCreatedEvent(
        "dataset", "new", "old", "main", "msg", "author", Instant.now(), "patch", 0);
    assertEquals(0, event.patchSize());
  }
}
