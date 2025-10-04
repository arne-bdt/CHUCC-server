package org.chucc.vcserver.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TagCreatedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    TagCreatedEvent event = new TagCreatedEvent(
        "test-dataset",
        "v1.0.0",
        "commit-id",
        now
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("v1.0.0", event.tagName());
    assertEquals("commit-id", event.commitId());
    assertEquals(now, event.timestamp());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent(null, "v1.0", "commit-id", Instant.now())
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new TagCreatedEvent("  ", "v1.0", "commit-id", Instant.now())
    );
  }

  @Test
  void testNullTagNameThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", null, "commit-id", Instant.now())
    );
  }

  @Test
  void testBlankTagNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new TagCreatedEvent("dataset", "", "commit-id", Instant.now())
    );
  }

  @Test
  void testNullCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", "v1.0", null, Instant.now())
    );
  }

  @Test
  void testNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new TagCreatedEvent("dataset", "v1.0", "commit-id", null)
    );
  }
}
