package org.chucc.vcserver.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Adds a unique trace ID to each request for log correlation.
 * Trace ID is added to MDC and automatically included in all log statements.
 */
@Component
public class TraceIdFilter implements Filter {

  private static final String TRACE_ID_KEY = "traceId";

  /**
   * Adds trace ID to MDC for the duration of the request.
   *
   * @param request the servlet request
   * @param response the servlet response
   * @param chain the filter chain
   * @throws IOException if I/O error occurs
   * @throws ServletException if servlet error occurs
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String traceId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put(TRACE_ID_KEY, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }
}
