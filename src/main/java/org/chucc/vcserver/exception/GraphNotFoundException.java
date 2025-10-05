package org.chucc.vcserver.exception;

/**
 * Thrown when a specified graph does not exist.
 * Error code: graph_not_found
 * HTTP status: 404 Not Found
 */
public class GraphNotFoundException extends VcException {

  public GraphNotFoundException(String message) {
    super(message, "graph_not_found", 404);
  }

  public GraphNotFoundException(String message, Throwable cause) {
    super(message, "graph_not_found", 404, cause);
  }
}
