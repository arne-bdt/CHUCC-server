package org.chucc.vcserver.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchTest {

  private static final CommitId COMMIT_ID_1 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440000");
  private static final CommitId COMMIT_ID_2 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440001");

  @Test
  void testCreateBranchWithValidName() {
    assertDoesNotThrow(() -> new Branch("main", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("feature-123", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("dev.branch", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("branch_name", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("Branch123", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("a1b2c3", COMMIT_ID_1));
  }

  @Test
  void testCreateBranchWithValidNameContainingAllowedCharacters() {
    // Test pattern ^[A-Za-z0-9._\-]+$
    assertDoesNotThrow(() -> new Branch("ABC123xyz", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("branch.with.dots", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("branch_with_underscores", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("branch-with-hyphens", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("complex.branch-name_123", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNullNameThrowsException() {
    assertThrows(NullPointerException.class, () -> new Branch(null, COMMIT_ID_1));
  }

  @Test
  void testConstructorWithBlankNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new Branch("", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("   ", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNullCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () -> new Branch("main", null));
  }

  @Test
  void testConstructorWithInvalidCharactersThrowsException() {
    // Test invalid characters (not matching ^[A-Za-z0-9._\-]+$)
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch/name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch@name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch#name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch$name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch!name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch*name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch+name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch=name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch:name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch;name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Branch("branch,name", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNonNfcNameThrowsException() {
    // Create a non-NFC normalized string
    // U+00E9 (é) is NFC, but U+0065 U+0301 (e + combining acute) is NFD
    String nfdString = "caf\u0065\u0301"; // café in NFD form
    String nfcString = "caf\u00e9"; // café in NFC form

    // NFD form should throw exception
    assertThrows(IllegalArgumentException.class, () -> new Branch(nfdString, COMMIT_ID_1));

    // NFC form should work (if it passes the pattern check)
    // Note: é is not in the allowed pattern, so this will fail pattern validation
    assertThrows(IllegalArgumentException.class, () -> new Branch(nfcString, COMMIT_ID_1));
  }

  @Test
  void testNfcValidationWithValidAsciiName() {
    // ASCII characters are always in NFC form
    assertDoesNotThrow(() -> new Branch("main", COMMIT_ID_1));
  }

  @Test
  void testGetName() {
    Branch branch = new Branch("develop", COMMIT_ID_1);
    assertEquals("develop", branch.getName());
  }

  @Test
  void testGetCommitId() {
    Branch branch = new Branch("main", COMMIT_ID_1);
    assertEquals(COMMIT_ID_1, branch.getCommitId());
  }

  @Test
  void testUpdateCommit() {
    Branch branch = new Branch("main", COMMIT_ID_1);
    assertEquals(COMMIT_ID_1, branch.getCommitId());

    branch.updateCommit(COMMIT_ID_2);
    assertEquals(COMMIT_ID_2, branch.getCommitId());
  }

  @Test
  void testUpdateCommitWithNullThrowsException() {
    Branch branch = new Branch("main", COMMIT_ID_1);
    assertThrows(NullPointerException.class, () -> branch.updateCommit(null));
  }

  @Test
  void testEquality() {
    Branch branch1 = new Branch("main", COMMIT_ID_1);
    Branch branch2 = new Branch("main", COMMIT_ID_1);
    Branch branch3 = new Branch("develop", COMMIT_ID_1);
    Branch branch4 = new Branch("main", COMMIT_ID_2);

    assertEquals(branch1, branch2);
    assertNotEquals(branch1, branch3); // Different name
    assertNotEquals(branch1, branch4); // Different commit
  }

  @Test
  void testHashCode() {
    Branch branch1 = new Branch("main", COMMIT_ID_1);
    Branch branch2 = new Branch("main", COMMIT_ID_1);
    Branch branch3 = new Branch("develop", COMMIT_ID_1);

    assertEquals(branch1.hashCode(), branch2.hashCode());
    assertNotEquals(branch1.hashCode(), branch3.hashCode());
  }

  @Test
  void testToString() {
    Branch branch = new Branch("main", COMMIT_ID_1);
    String toString = branch.toString();

    // Should contain both name and commit ID
    assertEquals("Branch{name='main', commitId=" + COMMIT_ID_1 + "}", toString);
  }

  @Test
  void testRefNameAbnfCompliance() {
    // Based on Git ref name rules (subset for our pattern)
    // Valid names:
    assertDoesNotThrow(() -> new Branch("main", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("feature.branch", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("bugfix-123", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("release_1.0", COMMIT_ID_1));

    // Invalid names:
    assertThrows(IllegalArgumentException.class,
        () -> new Branch("refs/heads/main", COMMIT_ID_1)); // Contains /
    assertThrows(IllegalArgumentException.class,
        () -> new Branch("branch name", COMMIT_ID_1)); // Contains space
  }

  @Test
  void testBranchNameWithDotsAndHyphens() {
    // Edge cases with dots and hyphens
    assertDoesNotThrow(() -> new Branch(".", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("-", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("_", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("...", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("---", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Branch("___", COMMIT_ID_1));
  }

  @Test
  void testBranchMutability() {
    Branch branch = new Branch("main", COMMIT_ID_1);

    // Name should not be mutable (no setter)
    assertEquals("main", branch.getName());

    // CommitId can be updated
    branch.updateCommit(COMMIT_ID_2);
    assertEquals(COMMIT_ID_2, branch.getCommitId());
  }
}
