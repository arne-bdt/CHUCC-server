package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a specified graph does not exist.
 * Error code: graph_not_found
 * HTTP status: 404 Not Found
 */
public class GraphNotFoundException extends VcException {
  
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new GraphNotFoundException with the specified message.
   *
   * @param message the detail message
   */
  public GraphNotFoundException(String message) {
    super(message, "graph_not_found", HttpStatus.NOT_FOUND);
  }

  /**
   * Constructs a new GraphNotFoundException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public GraphNotFoundException(String message, Throwable cause) {
    super(message, "graph_not_found", HttpStatus.NOT_FOUND, cause);
  }
}
