package org.chucc.vcserver.util;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility for validating identifier names (datasets, branches, tags).
 * Enforces naming conventions from protocol specification.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Pattern: ^[A-Za-z0-9._\-]+$ (alphanumeric, dot, underscore, hyphen)</li>
 *   <li>Unicode NFC normalization required</li>
 *   <li>Cannot be "." or ".."</li>
 *   <li>Cannot be Windows reserved device names (CON, PRN, AUX, NUL, COM1-9, LPT1-9)</li>
 *   <li>Cannot start with "_" (reserved for internal use)</li>
 *   <li>Cannot start or end with "." (for branches/tags only)</li>
 * </ul>
 */
public final class IdentifierValidator {

  /**
   * Maximum length for branch and tag names.
   */
  public static final int MAX_BRANCH_TAG_NAME_LENGTH = 255;

  private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

  private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
      "CON", "PRN", "AUX", "NUL",
      "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
      "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
  );

  private IdentifierValidator() {
    // Utility class
  }

  /**
   * Validates an identifier name (dataset, branch, or tag).
   *
   * @param name the identifier name to validate
   * @param maxLength maximum allowed length
   * @param type type name for error messages ("Dataset", "Branch", "Tag")
   * @throws IllegalArgumentException if validation fails
   */
  public static void validate(String name, int maxLength, String type) {
    Objects.requireNonNull(name, type + " name cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException(type + " name cannot be blank");
    }

    if (name.length() > maxLength) {
      throw new IllegalArgumentException(
          type + " name too long (max " + maxLength + " characters)");
    }

    // Normalize to NFC
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    if (!normalized.equals(name)) {
      throw new IllegalArgumentException(
          type + " name must be in Unicode NFC normalization form: " + name);
    }

    // Validate pattern
    if (!VALID_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
          type + " name must match pattern ^[A-Za-z0-9._\\-]+$: " + name);
    }

    // Reserved names
    if (name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException(type + " name cannot be '.' or '..'");
    }

    // Windows reserved names (case-insensitive)
    if (WINDOWS_RESERVED_NAMES.contains(name.toUpperCase())) {
      throw new IllegalArgumentException(
          type + " name cannot be a Windows reserved device name: " + name);
    }

    // Reject names starting with underscore
    if (name.startsWith("_")) {
      throw new IllegalArgumentException(
          type + " name cannot start with '_' (reserved for internal use): " + name);
    }

    // Reject names starting/ending with dot (branches/tags only)
    if (!type.equals("Dataset")) {
      if (name.startsWith(".")) {
        throw new IllegalArgumentException(type + " name cannot start with '.': " + name);
      }
      if (name.endsWith(".")) {
        throw new IllegalArgumentException(type + " name cannot end with '.': " + name);
      }
    }
  }
}
