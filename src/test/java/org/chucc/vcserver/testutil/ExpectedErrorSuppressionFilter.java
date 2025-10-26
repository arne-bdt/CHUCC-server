package org.chucc.vcserver.testutil;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Map;

/**
 * Logback filter that suppresses expected errors based on message patterns.
 *
 * <p>This filter uses SLF4J's MDC (Mapped Diagnostic Context) to allow individual tests
 * to suppress specific error messages while they are running. Tests specify exactly which
 * error messages they expect via {@link ExpectedErrorContext#suppress(String...)}.
 *
 * <p>The suppression is thread-local, so it only affects the test that set it up.
 * All other tests (including those running concurrently) will see all errors normally.
 *
 * <p>Usage:
 * <pre>{@code
 * &#64;Test
 * void testInvalidIri() {
 *   // Suppress only errors containing "Bad character in IRI"
 *   try (var ignored = ExpectedErrorContext.suppress("Bad character in IRI")) {
 *     GraphIdentifier.named("invalid iri"); // Error suppressed
 *   }
 *   // After try block, errors are visible again
 * }
 * }</pre>
 */
public class ExpectedErrorSuppressionFilter extends Filter<ILoggingEvent> {

  // MDC key for suppression patterns
  static final String MDC_SUPPRESS_PATTERNS = "test.suppress.patterns";

  /**
   * Decides whether to log a given event based on MDC suppression patterns.
   *
   * <p>Suppresses the event if:
   * <ul>
   *   <li>It's an ERROR or WARN level event</li>
   *   <li>The test has set suppression patterns in MDC</li>
   *   <li>The event's message or exception message contains one of the patterns</li>
   * </ul>
   *
   * @param event the logging event to evaluate
   * @return DENY if error matches suppression patterns, NEUTRAL otherwise
   */
  @Override
  public FilterReply decide(ILoggingEvent event) {
    // Only filter ERROR and WARN level events
    if (event.getLevel() != Level.ERROR && event.getLevel() != Level.WARN) {
      return FilterReply.NEUTRAL;
    }

    // Check if there are suppression patterns in MDC
    Map<String, String> mdc = event.getMDCPropertyMap();
    if (mdc == null || !mdc.containsKey(MDC_SUPPRESS_PATTERNS)) {
      return FilterReply.NEUTRAL;
    }

    String patternsStr = mdc.get(MDC_SUPPRESS_PATTERNS);
    if (patternsStr == null || patternsStr.isEmpty()) {
      return FilterReply.NEUTRAL;
    }

    // Split patterns
    String[] patterns = patternsStr.split("\\|\\|\\|");

    // Check formatted message
    String formattedMessage = event.getFormattedMessage();
    if (formattedMessage != null) {
      for (String pattern : patterns) {
        if (formattedMessage.contains(pattern)) {
          return FilterReply.DENY;
        }
      }
    }

    // Check exception message
    if (event.getThrowableProxy() != null) {
      String exceptionMessage = event.getThrowableProxy().getMessage();
      if (exceptionMessage != null) {
        for (String pattern : patterns) {
          if (exceptionMessage.contains(pattern)) {
            return FilterReply.DENY;
          }
        }
      }
    }

    return FilterReply.NEUTRAL;
  }
}
