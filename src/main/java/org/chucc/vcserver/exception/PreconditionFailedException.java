package org.chucc.vcserver.exception;

/**
 * Exception thrown when an If-Match precondition fails due to concurrent modification.
 *
 * <p>Returns HTTP 409 Conflict for version control concurrent write scenarios.
 * This indicates that the client's expected commit ID (from If-Match header)
 * does not match the current HEAD commit, signaling a concurrent modification conflict.
 */
public class PreconditionFailedException extends VcException {

  private final String expected;
  private final String actual;

  /**
   * Creates a new PreconditionFailedException.
   *
   * @param expected the expected commit ID from the If-Match header
   * @param actual the actual current HEAD commit ID
   */
  public PreconditionFailedException(String expected, String actual) {
    super(
        "If-Match precondition failed: expected " + expected + ", actual " + actual,
        "concurrent_modification_conflict",
        409
    );
    this.expected = expected;
    this.actual = actual;
  }

  /**
   * Gets the expected commit ID.
   *
   * @return the expected commit ID from the If-Match header
   */
  public String getExpected() {
    return expected;
  }

  /**
   * Gets the actual commit ID.
   *
   * @return the actual current HEAD commit ID
   */
  public String getActual() {
    return actual;
  }
}
