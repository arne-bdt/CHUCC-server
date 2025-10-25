package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TagCreatedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    TagCreatedEvent event = new TagCreatedEvent(
        "test-dataset",
        "v1.0.0",
        "commit-id",
        "Release version 1.0.0",
        "Alice",
        now
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("v1.0.0", event.tagName());
    assertEquals("commit-id", event.commitId());
    assertEquals("Release version 1.0.0", event.message());
    assertEquals("Alice", event.author());
    assertEquals(now, event.timestamp());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent(null, "v1.0", "commit-id", "msg", "Alice", Instant.now())
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new TagCreatedEvent("  ", "v1.0", "commit-id", "msg", "Alice", Instant.now())
    );
  }

  @Test
  void testNullTagNameThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", null, "commit-id", "msg", "Alice", Instant.now())
    );
  }

  @Test
  void testBlankTagNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new TagCreatedEvent("dataset", "", "commit-id", "msg", "Alice", Instant.now())
    );
  }

  @Test
  void testNullCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", "v1.0", null, "msg", "Alice", Instant.now())
    );
  }

  @Test
  void testNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", "v1.0", "commit-id", "msg", "Alice", null)
    );
  }
}
