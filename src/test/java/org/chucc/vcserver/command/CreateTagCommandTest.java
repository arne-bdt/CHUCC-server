package org.chucc.vcserver.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreateTagCommand validation.
 */
class CreateTagCommandTest {

  @Test
  void shouldCreateValidCommand() {
    CreateTagCommand command = new CreateTagCommand(
        "test-dataset",
        "v1.0",
        "commit-123",
        "Release version 1.0",
        "Alice");

    assertEquals("test-dataset", command.dataset());
    assertEquals("v1.0", command.tagName());
    assertEquals("commit-123", command.commitId());
    assertEquals("Release version 1.0", command.message());
    assertEquals("Alice", command.author());
  }

  @Test
  void shouldCreateCommandWithNullMessage() {
    CreateTagCommand command = new CreateTagCommand(
        "test-dataset",
        "v1.0",
        "commit-123",
        null,
        "Alice");

    assertNull(command.message());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new CreateTagCommand(null, "v1.0", "commit-123", "msg", "Alice"));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateTagCommand("", "v1.0", "commit-123", "msg", "Alice"));
  }

  @Test
  void shouldRejectNullTagName() {
    assertThrows(NullPointerException.class, () ->
        new CreateTagCommand("dataset", null, "commit-123", "msg", "Alice"));
  }

  @Test
  void shouldRejectBlankTagName() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateTagCommand("dataset", "", "commit-123", "msg", "Alice"));
  }

  @Test
  void shouldRejectNullCommitId() {
    assertThrows(NullPointerException.class, () ->
        new CreateTagCommand("dataset", "v1.0", null, "msg", "Alice"));
  }

  @Test
  void shouldRejectBlankCommitId() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateTagCommand("dataset", "v1.0", "", "msg", "Alice"));
  }
}
