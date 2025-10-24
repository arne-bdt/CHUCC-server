package org.chucc.vcserver.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for CreateBranchCommand validation.
 */
class CreateBranchCommandTest {

  @Test
  void shouldCreateValidCommand() {
    CreateBranchCommand command = new CreateBranchCommand(
        "test-dataset",
        "feature-branch",
        "main",
        false,
        "test-author");

    assertEquals("test-dataset", command.dataset());
    assertEquals("feature-branch", command.branchName());
    assertEquals("main", command.sourceRef());
    assertEquals(false, command.isProtected());
    assertEquals("test-author", command.author());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand(null, "branch", "main", false, "author"));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("", "branch", "main", false, "author"));
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand("dataset", null, "main", false, "author"));
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("dataset", "", "main", false, "author"));
  }

  @Test
  void shouldRejectNullSourceRef() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand("dataset", "branch", null, false, "author"));
  }

  @Test
  void shouldRejectBlankSourceRef() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("dataset", "branch", "", false, "author"));
  }

  @Test
  void shouldRejectNullAuthor() {
    assertThrows(NullPointerException.class, () ->
        new CreateBranchCommand("dataset", "branch", "main", false, null));
  }

  @Test
  void shouldRejectBlankAuthor() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateBranchCommand("dataset", "branch", "main", false, ""));
  }
}
