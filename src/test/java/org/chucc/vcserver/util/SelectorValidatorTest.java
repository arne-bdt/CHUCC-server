package org.chucc.vcserver.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.chucc.vcserver.exception.SelectorConflictException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SelectorValidator.
 */
class SelectorValidatorTest {

  @Test
  void testValidateOnlyBranch() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion("main", null, null));
  }

  @Test
  void testValidateOnlyCommit() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion(null, "abc123", null));
  }

  @Test
  void testValidateOnlyAsOf() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion(null, null, "2024-01-01T00:00:00Z"));
  }

  @Test
  void testValidateBranchAndAsOf() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion("main", null, "2024-01-01T00:00:00Z"));
  }

  @Test
  void testValidateAllNull() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion(null, null, null));
  }

  @Test
  void testValidateEmptyStrings() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion("", "", ""));
  }

  @Test
  void testInvalidBranchAndCommit() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> SelectorValidator.validateMutualExclusion("main", "abc123", null));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testInvalidCommitAndAsOf() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> SelectorValidator.validateMutualExclusion(null, "abc123", "2024-01-01T00:00:00Z"));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testInvalidAllThree() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> SelectorValidator.validateMutualExclusion(
            "main", "abc123", "2024-01-01T00:00:00Z"));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testBlankStringsAreConsideredNull() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion("  ", "", null));
  }

  @Test
  void testMixedBlankAndNonBlank() {
    assertDoesNotThrow(
        () -> SelectorValidator.validateMutualExclusion("main", "  ", null));
  }
}
