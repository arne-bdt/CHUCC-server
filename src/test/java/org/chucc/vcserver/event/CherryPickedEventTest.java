package org.chucc.vcserver.event;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CherryPickedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    CherryPickedEvent event = new CherryPickedEvent(
        "test-dataset",
        "new-commit-id",
        "source-commit-id",
        "main",
        "Cherry-pick feature X",
        "Alice <alice@example.com>",
        now,
        "H 1 .\n",
        3
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("new-commit-id", event.newCommitId());
    assertEquals("source-commit-id", event.sourceCommitId());
    assertEquals("main", event.branch());
    assertEquals("Cherry-pick feature X", event.message());
    assertEquals("Alice <alice@example.com>", event.author());
    assertEquals(now, event.timestamp());
    assertEquals("H 1 .\n", event.rdfPatch());
    assertEquals(3, event.patchSize());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent(null, "new", "source", "main", "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CherryPickedEvent("", "new", "source", "main", "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullNewCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", null, "source", "main", "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullSourceCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", null, "main", "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullBranchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", null, "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankBranchThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "  ", "msg", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", null, "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "  ", "author",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "msg", null,
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "msg", "",
            Instant.now(), "patch", 1)
    );
  }

  @Test
  void testNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "msg", "author",
            null, "patch", 1)
    );
  }

  @Test
  void testNullRdfPatchThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "msg", "author",
            Instant.now(), null, 1)
    );
  }

  @Test
  void testNegativePatchSizeThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new CherryPickedEvent("dataset", "new", "source", "main", "msg", "author",
            Instant.now(), "patch", -1)
    );
  }

  @Test
  void testZeroPatchSizeIsValid() {
    CherryPickedEvent event = new CherryPickedEvent(
        "dataset", "new", "source", "main", "msg", "author", Instant.now(), "patch", 0);
    assertEquals(0, event.patchSize());
  }
}
