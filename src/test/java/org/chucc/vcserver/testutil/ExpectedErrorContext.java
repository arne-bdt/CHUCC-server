package org.chucc.vcserver.testutil;

import java.util.Arrays;
import org.slf4j.MDC;

/**
 * Utility for suppressing expected errors in tests via SLF4J MDC.
 *
 * <p>Allows individual tests to specify exactly which error messages they expect,
 * suppressing only those specific errors while keeping all other errors visible.
 *
 * <p>The suppression is thread-local and automatically cleaned up via try-with-resources.
 *
 * <p>Examples:
 * <pre>{@code
 * &#64;Test
 * void testInvalidIri() {
 *   // Suppress only errors containing "Bad character in IRI"
 *   try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
 *     GraphIdentifier.named("invalid iri"); // Error suppressed
 *   }
 *   // Error suppression ends here - any other errors will be visible
 * }
 *
 * &#64;Test
 * void testMultipleErrors() {
 *   // Suppress multiple specific error patterns
 *   try (var ignored = ExpectedErrorContext.suppress(
 *       "Database connection failed",
 *       "Unexpected null value")) {
 *     // Code that triggers these specific errors
 *   }
 * }
 * }</pre>
 */
public final class ExpectedErrorContext {

  private ExpectedErrorContext() {
    // Utility class - prevent instantiation
  }

  // MDC key - must match ExpectedErrorSuppressionFilter.MDC_SUPPRESS_PATTERNS
  private static final String MDC_SUPPRESS_PATTERNS = "test.suppress.patterns";

  /**
   * Suppresses errors/warnings containing any of the specified message patterns.
   *
   * <p>The filter will suppress any ERROR or WARN level log event where the formatted
   * message or exception message contains one of the specified patterns.
   *
   * @param messagePatterns the message patterns to suppress (case-sensitive substrings)
   * @return an AutoCloseable that removes suppression when closed
   */
  public static AutoCloseable suppress(String... messagePatterns) {
    if (messagePatterns == null || messagePatterns.length == 0) {
      throw new IllegalArgumentException("At least one message pattern must be specified");
    }

    // Store patterns in MDC as a delimited string
    String patternsStr = String.join("|||", Arrays.asList(messagePatterns));
    MDC.put(MDC_SUPPRESS_PATTERNS, patternsStr);

    return () -> MDC.remove(MDC_SUPPRESS_PATTERNS);
  }
}
