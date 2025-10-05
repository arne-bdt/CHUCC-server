package org.chucc.vcserver.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for ResetBranchCommand validation.
 */
class ResetBranchCommandTest {

  @Test
  void shouldCreateValidCommand() {
    ResetBranchCommand command = new ResetBranchCommand(
        "test-dataset",
        "main",
        "commit-456");

    assertEquals("test-dataset", command.dataset());
    assertEquals("main", command.branchName());
    assertEquals("commit-456", command.toCommitId());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new ResetBranchCommand(null, "main", "commit-456"));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new ResetBranchCommand("", "main", "commit-456"));
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThrows(NullPointerException.class, () ->
        new ResetBranchCommand("dataset", null, "commit-456"));
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThrows(IllegalArgumentException.class, () ->
        new ResetBranchCommand("dataset", "", "commit-456"));
  }

  @Test
  void shouldRejectNullToCommitId() {
    assertThrows(NullPointerException.class, () ->
        new ResetBranchCommand("dataset", "main", null));
  }

  @Test
  void shouldRejectBlankToCommitId() {
    assertThrows(IllegalArgumentException.class, () ->
        new ResetBranchCommand("dataset", "main", ""));
  }
}
