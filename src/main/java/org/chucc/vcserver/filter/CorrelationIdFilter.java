package org.chucc.vcserver.filter;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that generates a correlation ID for each HTTP request.
 * The correlation ID is stored in SLF4J MDC for logging and can be
 * retrieved by EventPublisher to add to Kafka event headers.
 *
 * <p>Correlation IDs enable distributed tracing across:
 * HTTP Request → Controller → EventPublisher → Kafka → ReadModelProjector
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  /**
   * MDC key for correlation ID (used in logging pattern and EventPublisher).
   */
  public static final String CORRELATION_ID_KEY = "correlationId";

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {
    // Generate UUIDv7 (time-ordered) for this request
    String correlationId = UuidCreator.getTimeOrderedEpoch().toString();

    // Store in MDC for logging
    MDC.put(CORRELATION_ID_KEY, correlationId);

    try {
      // Process request (correlation ID available throughout)
      filterChain.doFilter(request, response);
    } finally {
      // Clean up MDC after request completes
      MDC.remove(CORRELATION_ID_KEY);
    }
  }
}
