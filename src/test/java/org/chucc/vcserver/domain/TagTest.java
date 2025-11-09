package org.chucc.vcserver.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TagTest {

  private static final CommitId COMMIT_ID_1 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440000");
  private static final CommitId COMMIT_ID_2 =
      CommitId.of("550e8400-e29b-41d4-a716-446655440001");

  @Test
  void testCreateTagWithValidName() {
    assertDoesNotThrow(() -> new Tag("v1.0.0", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("release-1.2.3", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("stable", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("tag_name", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("Tag123", COMMIT_ID_1));
  }

  @Test
  void testCreateTagWithValidNameContainingAllowedCharacters() {
    // Test pattern ^[A-Za-z0-9._\-]+$
    assertDoesNotThrow(() -> new Tag("ABC123xyz", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("tag.with.dots", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("tag_with_underscores", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("tag-with-hyphens", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("v1.0.0-rc.1", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNullNameThrowsException() {
    assertThrows(NullPointerException.class, () -> new Tag(null, COMMIT_ID_1));
  }

  @Test
  void testConstructorWithBlankNameThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new Tag("", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("   ", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNullCommitIdThrowsException() {
    assertThrows(NullPointerException.class, () -> new Tag("v1.0.0", null));
  }

  @Test
  void testConstructorWithInvalidCharactersThrowsException() {
    // Test invalid characters (not matching ^[A-Za-z0-9._\-]+$)
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag/name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag@name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag#name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag$name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag!name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag*name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag+name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag=name", COMMIT_ID_1));
    assertThrows(IllegalArgumentException.class, () -> new Tag("tag:name", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithNonNfcNameThrowsException() {
    // Create a non-NFC normalized string
    // U+00E9 (é) is NFC, but U+0065 U+0301 (e + combining acute) is NFD
    String nfdString = "v1.0.0-caf\u0065\u0301"; // café in NFD form
    String nfcString = "v1.0.0-caf\u00e9"; // café in NFC form

    // NFD form should throw exception
    assertThrows(IllegalArgumentException.class, () -> new Tag(nfdString, COMMIT_ID_1));

    // NFC form should work (if it passes the pattern check)
    // Note: é is not in the allowed pattern, so this will fail pattern validation
    assertThrows(IllegalArgumentException.class, () -> new Tag(nfcString, COMMIT_ID_1));
  }

  @Test
  void testNfcValidationWithValidAsciiName() {
    // ASCII characters are always in NFC form
    assertDoesNotThrow(() -> new Tag("v1.0.0", COMMIT_ID_1));
  }

  @Test
  void testGetName() {
    Tag tag = new Tag("v1.0.0", COMMIT_ID_1);
    assertEquals("v1.0.0", tag.name());
  }

  @Test
  void testGetCommitId() {
    Tag tag = new Tag("v1.0.0", COMMIT_ID_1);
    assertEquals(COMMIT_ID_1, tag.commitId());
  }

  @Test
  void testEquality() {
    Instant now = Instant.now();
    Tag tag1 = new Tag("v1.0.0", COMMIT_ID_1, null, null, now);
    Tag tag2 = new Tag("v1.0.0", COMMIT_ID_1, null, null, now);
    Tag tag3 = new Tag("v2.0.0", COMMIT_ID_1, null, null, now);
    Tag tag4 = new Tag("v1.0.0", COMMIT_ID_2, null, null, now);

    assertEquals(tag1, tag2);
    assertNotEquals(tag1, tag3); // Different name
    assertNotEquals(tag1, tag4); // Different commit
  }

  @Test
  void testHashCode() {
    Instant now = Instant.now();
    Tag tag1 = new Tag("v1.0.0", COMMIT_ID_1, null, null, now);
    Tag tag2 = new Tag("v1.0.0", COMMIT_ID_1, null, null, now);
    Tag tag3 = new Tag("v2.0.0", COMMIT_ID_1, null, null, now);

    assertEquals(tag1.hashCode(), tag2.hashCode());
    assertNotEquals(tag1.hashCode(), tag3.hashCode());
  }

  @Test
  void testToString() {
    Tag tag = new Tag("v1.0.0", COMMIT_ID_1);
    String toString = tag.toString();

    // Should contain name, commit ID, message, author, and createdAt
    assertTrue(toString.contains("name='v1.0.0'"));
    assertTrue(toString.contains("commitId=" + COMMIT_ID_1));
    assertTrue(toString.contains("message='null'"));
    assertTrue(toString.contains("author='null'"));
    assertTrue(toString.contains("createdAt="));
  }

  @Test
  void testImmutability() {
    // Tag is a record, so it's inherently immutable
    Tag tag = new Tag("v1.0.0", COMMIT_ID_1);

    // Accessing fields should always return the same values
    assertEquals("v1.0.0", tag.name());
    assertEquals(COMMIT_ID_1, tag.commitId());

    // No setters should exist (compile-time check, but verified by design)
  }

  @Test
  void testTagNameWithDotsAndHyphens() {
    // Common tag naming patterns
    assertDoesNotThrow(() -> new Tag("v1.2.3", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("v1.0.0-alpha", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("v1.0.0-rc.1", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("release-2024.01.15", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("snapshot_20241004", COMMIT_ID_1));
  }

  @Test
  void testRefNameAbnfCompliance() {
    // Based on Git ref name rules (subset for our pattern)
    // Valid names:
    assertDoesNotThrow(() -> new Tag("v1.0.0", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("stable.release", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("hotfix-123", COMMIT_ID_1));

    // Invalid names:
    assertThrows(IllegalArgumentException.class,
        () -> new Tag("refs/tags/v1.0.0", COMMIT_ID_1)); // Contains /
    assertThrows(IllegalArgumentException.class,
        () -> new Tag("tag name", COMMIT_ID_1)); // Contains space
  }

  @Test
  void testSingleCharacterTags() {
    // Edge cases with single characters
    assertDoesNotThrow(() -> new Tag("v", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("1", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("-", COMMIT_ID_1));
    // Note: "." and "_" are rejected by reserved name validation
  }

  @Test
  void testConstructorWithSingleDotThrowsException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new Tag(".", COMMIT_ID_1));
    assertEquals("Tag name cannot be '.' or '..'", exception.getMessage());
  }

  @Test
  void testConstructorWithDoubleDotThrowsException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new Tag("..", COMMIT_ID_1));
    assertEquals("Tag name cannot be '.' or '..'", exception.getMessage());
  }

  @Test
  void testConstructorWithNameStartingWithUnderscoreThrowsException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new Tag("_temp", COMMIT_ID_1));
    assertEquals("Tag name cannot start with '_' (reserved for internal use): _temp",
        exception.getMessage());
  }

  @Test
  void testConstructorWithNameStartingWithDotThrowsException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new Tag(".temp", COMMIT_ID_1));
    assertEquals("Tag name cannot start with '.': .temp", exception.getMessage());
  }

  @Test
  void testConstructorWithNameEndingWithDotThrowsException() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> new Tag("tag.", COMMIT_ID_1));
    assertEquals("Tag name cannot end with '.': tag.", exception.getMessage());
  }

  @Test
  void testConstructorWithMultipleDotsInMiddleAllowed() {
    // Dots in the middle are allowed (common in version tags)
    assertDoesNotThrow(() -> new Tag("v1.0.0", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("release.2024.01", COMMIT_ID_1));
  }

  @Test
  void testConstructorWithUnderscoreInMiddleAllowed() {
    // Underscores in the middle are allowed
    assertDoesNotThrow(() -> new Tag("release_candidate", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("v1_0_0", COMMIT_ID_1));
  }

  @Test
  void testSemanticVersioningTags() {
    // Common semantic versioning patterns
    assertDoesNotThrow(() -> new Tag("1.0.0", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("2.1.3", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("1.0.0-alpha.1", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("2.0.0-beta.2", COMMIT_ID_1));
    assertDoesNotThrow(() -> new Tag("3.0.0-rc.1", COMMIT_ID_1));
  }
}
