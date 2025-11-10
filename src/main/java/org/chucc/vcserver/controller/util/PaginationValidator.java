package org.chucc.vcserver.controller.util;

/**
 * Utility class for validating pagination parameters.
 */
public final class PaginationValidator {

  private static final int MAX_LIMIT = 1000;

  /**
   * Private constructor to prevent instantiation.
   */
  private PaginationValidator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Validates pagination parameters.
   *
   * @param limit the limit parameter
   * @param offset the offset parameter
   * @throws IllegalArgumentException if parameters are invalid
   */
  public static void validate(Integer limit, Integer offset) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
    }
    if (offset < 0) {
      throw new IllegalArgumentException("Offset cannot be negative");
    }
  }
}
