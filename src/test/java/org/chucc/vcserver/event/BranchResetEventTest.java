package org.chucc.vcserver.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BranchResetEventTest {

  @Test
  void testCreateEventWithValidData() {
    Instant now = Instant.now();
    BranchResetEvent event = new BranchResetEvent(
        "test-dataset",
        "main",
        "old-commit-id",
        "new-commit-id",
        now
    );

    assertNotNull(event);
    assertEquals("test-dataset", event.dataset());
    assertEquals("main", event.branchName());
    assertEquals("old-commit-id", event.fromCommitId());
    assertEquals("new-commit-id", event.toCommitId());
    assertEquals(now, event.timestamp());
  }

  @Test
  void testNullDatasetThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchResetEvent(null, "main", "from", "to", Instant.now())
    );
  }

  @Test
  void testBlankDatasetThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchResetEvent("", "main", "from", "to", Instant.now())
    );
  }

  @Test
  void testNullBranchNameThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchResetEvent("dataset", null, "from", "to", Instant.now())
    );
  }

  @Test
  void testBlankBranchNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () ->
        new BranchResetEvent("dataset", "  ", "from", "to", Instant.now())
    );
  }

  @Test
  void testNullFromCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchResetEvent("dataset", "main", null, "to", Instant.now())
    );
  }

  @Test
  void testNullToCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchResetEvent("dataset", "main", "from", null, Instant.now())
    );
  }

  @Test
  void testNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () ->
        new BranchResetEvent("dataset", "main", "from", "to", null)
    );
  }
}
