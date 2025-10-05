package org.chucc.vcserver.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for RevertCommitCommand validation.
 */
class RevertCommitCommandTest {

  @Test
  void shouldCreateValidCommand() {
    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        "commit-789",
        "Revert bad changes",
        "bob");

    assertEquals("test-dataset", command.dataset());
    assertEquals("main", command.branchName());
    assertEquals("commit-789", command.commitId());
    assertEquals("Revert bad changes", command.message());
    assertEquals("bob", command.author());
  }

  @Test
  void shouldCreateCommandWithNullMessage() {
    RevertCommitCommand command = new RevertCommitCommand(
        "test-dataset",
        "main",
        "commit-789",
        null,
        "bob");

    assertNull(command.message());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new RevertCommitCommand(null, "main", "commit-789", "msg", "bob"));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCommitCommand("", "main", "commit-789", "msg", "bob"));
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThrows(NullPointerException.class, () ->
        new RevertCommitCommand("dataset", null, "commit-789", "msg", "bob"));
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCommitCommand("dataset", "", "commit-789", "msg", "bob"));
  }

  @Test
  void shouldRejectNullCommitId() {
    assertThrows(NullPointerException.class, () ->
        new RevertCommitCommand("dataset", "main", null, "msg", "bob"));
  }

  @Test
  void shouldRejectBlankCommitId() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCommitCommand("dataset", "main", "", "msg", "bob"));
  }

  @Test
  void shouldRejectNullAuthor() {
    assertThrows(NullPointerException.class, () ->
        new RevertCommitCommand("dataset", "main", "commit-789", "msg", null));
  }

  @Test
  void shouldRejectBlankAuthor() {
    assertThrows(IllegalArgumentException.class, () ->
        new RevertCommitCommand("dataset", "main", "commit-789", "msg", ""));
  }
}
