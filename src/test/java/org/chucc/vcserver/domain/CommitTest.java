package org.chucc.vcserver.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommitTest {

  private static final CommitId COMMIT_ID_1 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440000");
  private static final CommitId COMMIT_ID_2 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440001");
  private static final CommitId COMMIT_ID_3 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440002");

  @Test
  void testCreateCommitWithValidData() {
    Commit commit = new Commit(
        COMMIT_ID_1,
        List.of(),
        "Alice <alice@example.com>",
        "Initial commit",
        Instant.now()
    );

    assertNotNull(commit);
    assertEquals(COMMIT_ID_1, commit.id());
    assertEquals(0, commit.parents().size());
    assertEquals("Alice <alice@example.com>", commit.author());
    assertEquals("Initial commit", commit.message());
    assertNotNull(commit.timestamp());
  }

  @Test
  void testCreateCommitWithParents() {
    List<CommitId> parents = List.of(COMMIT_ID_1, COMMIT_ID_2);
    Commit commit = new Commit(
        COMMIT_ID_3,
        parents,
        "Bob <bob@example.com>",
        "Merge commit",
        Instant.now()
    );

    assertEquals(2, commit.parents().size());
    assertEquals(COMMIT_ID_1, commit.parents().get(0));
    assertEquals(COMMIT_ID_2, commit.parents().get(1));
  }

  @Test
  void testCreateFactoryMethod() {
    Commit commit = Commit.create(
        List.of(COMMIT_ID_1),
        "Charlie <charlie@example.com>",
        "Add feature"
    );

    assertNotNull(commit);
    assertNotNull(commit.id());
    assertEquals(1, commit.parents().size());
    assertEquals(COMMIT_ID_1, commit.parents().get(0));
    assertEquals("Charlie <charlie@example.com>", commit.author());
    assertEquals("Add feature", commit.message());
    assertNotNull(commit.timestamp());
  }

  @Test
  void testConstructorWithNullIdThrowsException() {
    assertThrows(NullPointerException.class, () -> new Commit(
        null,
        List.of(),
        "Author",
        "Message",
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithNullParentsThrowsException() {
    assertThrows(NullPointerException.class, () -> new Commit(
        COMMIT_ID_1,
        null,
        "Author",
        "Message",
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithNullAuthorThrowsException() {
    assertThrows(NullPointerException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        null,
        "Message",
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithBlankAuthorThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "",
        "Message",
        Instant.now()
    ));

    assertThrows(IllegalArgumentException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "   ",
        "Message",
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithNullMessageThrowsException() {
    assertThrows(NullPointerException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        null,
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithBlankMessageThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "",
        Instant.now()
    ));

    assertThrows(IllegalArgumentException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "   ",
        Instant.now()
    ));
  }

  @Test
  void testConstructorWithNullTimestampThrowsException() {
    assertThrows(NullPointerException.class, () -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "Message",
        null
    ));
  }

  @Test
  void testIsInitialReturnsTrueForCommitWithoutParents() {
    Commit commit = new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "Initial commit",
        Instant.now()
    );

    assertTrue(commit.isInitial());
    assertFalse(commit.isMerge());
  }

  @Test
  void testIsInitialReturnsFalseForCommitWithOneParent() {
    Commit commit = new Commit(
        COMMIT_ID_2,
        List.of(COMMIT_ID_1),
        "Author",
        "Second commit",
        Instant.now()
    );

    assertFalse(commit.isInitial());
    assertFalse(commit.isMerge());
  }

  @Test
  void testIsMergeReturnsTrueForCommitWithMultipleParents() {
    Commit commit = new Commit(
        COMMIT_ID_3,
        List.of(COMMIT_ID_1, COMMIT_ID_2),
        "Author",
        "Merge commit",
        Instant.now()
    );

    assertFalse(commit.isInitial());
    assertTrue(commit.isMerge());
  }

  @Test
  void testParentsListIsImmutable() {
    List<CommitId> mutableList = new java.util.ArrayList<>();
    mutableList.add(COMMIT_ID_1);

    Commit commit = new Commit(
        COMMIT_ID_2,
        mutableList,
        "Author",
        "Message",
        Instant.now()
    );

    // Modify original list
    mutableList.add(COMMIT_ID_2);

    // Commit's parents should not be affected
    assertEquals(1, commit.parents().size());

    // Trying to modify the returned list should throw exception
    assertThrows(UnsupportedOperationException.class,
        () -> commit.parents().add(COMMIT_ID_3));
  }

  @Test
  void testCommitEquality() {
    Instant timestamp = Instant.now();
    Commit commit1 = new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "Message",
        timestamp
    );
    Commit commit2 = new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        "Message",
        timestamp
    );

    assertEquals(commit1, commit2);
    assertEquals(commit1.hashCode(), commit2.hashCode());
  }

  @Test
  void testMultilineCommitMessage() {
    String multilineMessage = "Fix bug in parser\n\nThis commit addresses the issue where...\n- Fixed A\n- Fixed B";

    assertDoesNotThrow(() -> new Commit(
        COMMIT_ID_1,
        List.of(),
        "Author",
        multilineMessage,
        Instant.now()
    ));
  }
}
