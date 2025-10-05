package org.chucc.vcserver.command;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreateCommitCommand validation.
 */
class CreateCommitCommandTest {

  @Test
  void shouldCreateValidCommand() {
    CreateCommitCommand command = new CreateCommitCommand(
        "test-dataset",
        "main",
        "INSERT DATA { <s> <p> <o> }",
        "Add triple",
        "alice",
        Map.of("key", "value"));

    assertEquals("test-dataset", command.dataset());
    assertEquals("main", command.branchName());
    assertEquals("INSERT DATA { <s> <p> <o> }", command.sparqlUpdate());
    assertEquals("Add triple", command.message());
    assertEquals("alice", command.author());
    assertNotNull(command.metadata());
    assertEquals("value", command.metadata().get("key"));
  }

  @Test
  void shouldCreateCommandWithNullMetadata() {
    CreateCommitCommand command = new CreateCommitCommand(
        "test-dataset",
        "main",
        "INSERT DATA { <s> <p> <o> }",
        "Add triple",
        "alice",
        null);

    assertNotNull(command.metadata());
    assertTrue(command.metadata().isEmpty());
  }

  @Test
  void shouldRejectNullDataset() {
    assertThrows(NullPointerException.class, () ->
        new CreateCommitCommand(null, "main", "UPDATE", "msg", "author", null));
  }

  @Test
  void shouldRejectBlankDataset() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateCommitCommand("", "main", "UPDATE", "msg", "author", null));
  }

  @Test
  void shouldRejectNullBranchName() {
    assertThrows(NullPointerException.class, () ->
        new CreateCommitCommand("dataset", null, "UPDATE", "msg", "author", null));
  }

  @Test
  void shouldRejectBlankBranchName() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateCommitCommand("dataset", "", "UPDATE", "msg", "author", null));
  }

  @Test
  void shouldRejectNullSparqlUpdate() {
    assertThrows(NullPointerException.class, () ->
        new CreateCommitCommand("dataset", "main", null, "msg", "author", null));
  }

  @Test
  void shouldRejectBlankSparqlUpdate() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateCommitCommand("dataset", "main", "", "msg", "author", null));
  }

  @Test
  void shouldRejectNullMessage() {
    assertThrows(NullPointerException.class, () ->
        new CreateCommitCommand("dataset", "main", "UPDATE", null, "author", null));
  }

  @Test
  void shouldRejectBlankMessage() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateCommitCommand("dataset", "main", "UPDATE", "", "author", null));
  }

  @Test
  void shouldRejectNullAuthor() {
    assertThrows(NullPointerException.class, () ->
        new CreateCommitCommand("dataset", "main", "UPDATE", "msg", null, null));
  }

  @Test
  void shouldRejectBlankAuthor() {
    assertThrows(IllegalArgumentException.class, () ->
        new CreateCommitCommand("dataset", "main", "UPDATE", "msg", "", null));
  }
}
