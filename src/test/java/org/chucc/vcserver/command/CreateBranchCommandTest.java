package org.chucc.vcserver.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for CreateBranchCommand validation.
 */
class CreateBranchCommandTest {

  @Test
  void shouldCreateValidCommand() {
    CreateBranchCommand command = new CreateBranchCommand(
        "test-dataset",
        "feature-branch",
        "commit-123");

    assertEquals("test-dataset", command.dataset());
    assertEquals("feature-branch", command.branchName());
    assertEquals("commit-123", command.fromCommitId());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand(null, "branch", "commit-123"));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("", "branch", "commit-123"));
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand("dataset", null, "commit-123"));
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("dataset", "", "commit-123"));
  }

  @Test
  void shouldRejectNullFromCommitId() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand("dataset", "branch", null));
  }

  @Test
  void shouldRejectBlankFromCommitId() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("dataset", "branch", ""));
  }
}
