package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when graph reference is invalid.
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>Missing required field for source type</li>
 *   <li>Conflicting fields (e.g., dataset with source=inline)</li>
 *   <li>Multiple selectors (branch + commit + asOf)</li>
 *   <li>Invalid graph URI</li>
 * </ul>
 *
 * <p>Returns HTTP 400 Bad Request with error type 'invalid_graph_reference'.</p>
 */
public class InvalidGraphReferenceException extends VcException {

  private static final long serialVersionUID = 1L;

  /**
   * Construct exception with message.
   *
   * @param message error message
   */
  public InvalidGraphReferenceException(String message) {
    super(message, "invalid_graph_reference", HttpStatus.BAD_REQUEST);
  }
}
