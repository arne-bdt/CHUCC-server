package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BranchCreatedEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    BranchCreatedEvent event = new BranchCreatedEvent(
        "test-dataset",
        "main",
        "550e8400-e29b-41d4-a716-446655440000",
        "initial",
        false,
        "test-author",
        now
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("main", event.branchName());
    assertEquals("550e8400-e29b-41d4-a716-446655440000", event.commitId());
    assertEquals("initial", event.sourceRef());
    assertFalse(event.isProtected());
    assertEquals("test-author", event.author());
    assertEquals(now, event.timestamp());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent(null, "main", "commit-id", "main", false, "author", Instant.now())
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchCreatedEvent("  ", "main", "commit-id", "main", false, "author", Instant.now())
    );
  }

  @Test
  void testNullBranchNameThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent("dataset", null, "commit-id", "main", false, "author", Instant.now())
    );
  }

  @Test
  void testBlankBranchNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchCreatedEvent("dataset", "", "commit-id", "main", false, "author", Instant.now())
    );
  }

  @Test
  void testNullCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent("dataset", "main", null, "main", false, "author", Instant.now())
    );
  }

  @Test
  void testNullSourceRefThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent("dataset", "main", "commit-id", null, false, "author", Instant.now())
    );
  }

  @Test
  void testBlankSourceRefThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchCreatedEvent("dataset", "main", "commit-id", "  ", false, "author", Instant.now())
    );
  }

  @Test
  void testNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent("dataset", "main", "commit-id", "main", false, null, Instant.now())
    );
  }

  @Test
  void testBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchCreatedEvent("dataset", "main", "commit-id", "main", false, "", Instant.now())
    );
  }

  @Test
  void testNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchCreatedEvent("dataset", "main", "commit-id", "main", false, "author", null)
    );
  }

  @Test
  void testProtectedBranchCreation() {
    Instant now = Instant.now();
    BranchCreatedEvent event = new BranchCreatedEvent(
        "test-dataset",
        "main",
        "550e8400-e29b-41d4-a716-446655440000",
        "initial",
        true,  // protected
        "admin",
        now
    );

    assertTrue(event.isProtected());
  }

  @Test
  void testRecordEquality() {
    Instant now = Instant.now();
    // Use canonical constructor with same eventId for equality testing
    BranchCreatedEvent event1 = new BranchCreatedEvent(
        "test-event-id", "dataset", "main", "commit-id", "main", false, "author", now);
    BranchCreatedEvent event2 = new BranchCreatedEvent(
        "test-event-id", "dataset", "main", "commit-id", "main", false, "author", now);

    assertEquals(event1, event2);
    assertEquals(event1.hashCode(), event2.hashCode());
  }
}
