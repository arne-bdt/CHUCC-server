package org.chucc.vcserver.event;

/**
 * Utility class for common event validation.
 */
final class EventValidation {

  private EventValidation() {
    // Utility class - prevent instantiation
  }

  /**
   * Validates that a string field is not blank.
   *
   * @param value the value to validate
   * @param fieldName the name of the field (for error messages)
   * @throws IllegalArgumentException if the value is blank
   */
  static void requireNonBlank(String value, String fieldName) {
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " cannot be blank");
    }
  }
}
