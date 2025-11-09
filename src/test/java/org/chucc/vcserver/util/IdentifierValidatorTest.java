package org.chucc.vcserver.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for IdentifierValidator.
 */
class IdentifierValidatorTest {

  // Valid names

  @Test
  void validate_validDatasetName_shouldNotThrow() {
    assertDoesNotThrow(() -> IdentifierValidator.validate("my-dataset", 249, "Dataset"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("dataset_123", 249, "Dataset"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("test.dataset", 249, "Dataset"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("ABC-123", 249, "Dataset"));
  }

  @Test
  void validate_validBranchName_shouldNotThrow() {
    assertDoesNotThrow(() -> IdentifierValidator.validate("main", 255, "Branch"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("feature-123", 255, "Branch"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("dev.branch", 255, "Branch"));
  }

  @Test
  void validate_validTagName_shouldNotThrow() {
    assertDoesNotThrow(() -> IdentifierValidator.validate("v1.0.0", 255, "Tag"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("release-2024", 255, "Tag"));
  }

  // Null/blank tests

  @Test
  void validate_nullName_shouldThrow() {
    NullPointerException exception = assertThrows(NullPointerException.class,
        () -> IdentifierValidator.validate(null, 249, "Dataset"));
    assertEquals("Dataset name cannot be null", exception.getMessage());
  }

  @Test
  void validate_blankName_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("", 249, "Dataset"));
    assertEquals("Dataset name cannot be blank", exception.getMessage());
  }

  @Test
  void validate_whitespaceOnlyName_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("   ", 249, "Dataset"));
    assertEquals("Dataset name cannot be blank", exception.getMessage());
  }

  // Length tests

  @Test
  void validate_nameTooLong_shouldThrow() {
    String longName = "a".repeat(250);
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate(longName, 249, "Dataset"));
    assertEquals("Dataset name too long (max 249 characters)", exception.getMessage());
  }

  @Test
  void validate_nameAtMaxLength_shouldNotThrow() {
    String maxName = "a".repeat(249);
    assertDoesNotThrow(() -> IdentifierValidator.validate(maxName, 249, "Dataset"));
  }

  // NFC normalization tests

  @Test
  void validate_nonNfcName_shouldThrow() {
    // U+0065 U+0301 (e + combining acute) is NFD form
    String nfdString = "caf\u0065\u0301";
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate(nfdString, 249, "Dataset"));
    assertEquals("Dataset name must be in Unicode NFC normalization form: caf\u0065\u0301",
        exception.getMessage());
  }

  @Test
  void validate_nfcName_shouldNotThrow() {
    // ASCII is always in NFC form
    assertDoesNotThrow(() -> IdentifierValidator.validate("main", 255, "Branch"));
  }

  // Pattern validation tests

  @Test
  void validate_invalidCharacters_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("name/with/slash", 249, "Dataset"));
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("name with space", 249, "Dataset"));
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("name@domain", 249, "Dataset"));
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("name#tag", 249, "Dataset"));
  }

  // Reserved names tests (. and ..)

  @Test
  void validate_singleDot_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate(".", 249, "Dataset"));
    assertEquals("Dataset name cannot be '.' or '..'", exception.getMessage());
  }

  @Test
  void validate_doubleDot_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("..", 249, "Dataset"));
    assertEquals("Dataset name cannot be '.' or '..'", exception.getMessage());
  }

  // Windows reserved names tests

  @Test
  void validate_windowsReservedName_CON_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("CON", 249, "Dataset"));
    assertEquals("Dataset name cannot be a Windows reserved device name: CON",
        exception.getMessage());
  }

  @Test
  void validate_windowsReservedName_con_caseInsensitive_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("con", 249, "Dataset"));
    assertEquals("Dataset name cannot be a Windows reserved device name: con",
        exception.getMessage());
  }

  @Test
  void validate_windowsReservedName_PRN_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("PRN", 255, "Branch"));
  }

  @Test
  void validate_windowsReservedName_AUX_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("AUX", 255, "Tag"));
  }

  @Test
  void validate_windowsReservedName_NUL_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("NUL", 249, "Dataset"));
  }

  @Test
  void validate_windowsReservedName_COM1_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("COM1", 255, "Branch"));
  }

  @Test
  void validate_windowsReservedName_COM9_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("COM9", 249, "Dataset"));
  }

  @Test
  void validate_windowsReservedName_LPT1_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("LPT1", 255, "Tag"));
  }

  @Test
  void validate_windowsReservedName_LPT9_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("LPT9", 255, "Branch"));
  }

  @Test
  void validate_similarToReservedName_CONN_shouldNotThrow() {
    // "CONN" is not reserved (only "CON")
    assertDoesNotThrow(() -> IdentifierValidator.validate("CONN", 249, "Dataset"));
  }

  @Test
  void validate_similarToReservedName_COM10_shouldNotThrow() {
    // "COM10" is not reserved (only COM1-COM9)
    assertDoesNotThrow(() -> IdentifierValidator.validate("COM10", 249, "Dataset"));
  }

  @Test
  void validate_similarToReservedName_LPT0_shouldNotThrow() {
    // "LPT0" is not reserved (only LPT1-LPT9)
    assertDoesNotThrow(() -> IdentifierValidator.validate("LPT0", 255, "Branch"));
  }

  // Underscore prefix tests

  @Test
  void validate_nameStartingWithUnderscore_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("_internal", 249, "Dataset"));
    assertEquals("Dataset name cannot start with '_' (reserved for internal use): _internal",
        exception.getMessage());
  }

  @Test
  void validate_nameWithUnderscoreInMiddle_shouldNotThrow() {
    assertDoesNotThrow(() -> IdentifierValidator.validate("my_dataset", 249, "Dataset"));
    assertDoesNotThrow(() -> IdentifierValidator.validate("test_branch", 255, "Branch"));
  }

  // Dot prefix/suffix tests (branches/tags only)

  @Test
  void validate_branchNameStartingWithDot_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate(".hidden", 255, "Branch"));
    assertEquals("Branch name cannot start with '.': .hidden", exception.getMessage());
  }

  @Test
  void validate_branchNameEndingWithDot_shouldThrow() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("branch.", 255, "Branch"));
    assertEquals("Branch name cannot end with '.': branch.", exception.getMessage());
  }

  @Test
  void validate_tagNameStartingWithDot_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate(".temp", 255, "Tag"));
  }

  @Test
  void validate_tagNameEndingWithDot_shouldThrow() {
    assertThrows(IllegalArgumentException.class,
        () -> IdentifierValidator.validate("tag.", 255, "Tag"));
  }

  @Test
  void validate_datasetNameWithDotsInMiddle_shouldNotThrow() {
    // Datasets CAN have dots in the middle (no start/end restriction)
    assertDoesNotThrow(() -> IdentifierValidator.validate("my.dataset.name", 249, "Dataset"));
  }

  @Test
  void validate_branchNameWithDotsInMiddle_shouldNotThrow() {
    // Branches can have dots in the middle (just not start/end)
    assertDoesNotThrow(() -> IdentifierValidator.validate("feature.1.0", 255, "Branch"));
  }
}
