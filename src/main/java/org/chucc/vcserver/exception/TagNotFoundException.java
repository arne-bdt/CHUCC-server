package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested tag does not exist.
 * Maps to HTTP 404 Not Found with error code "tag_not_found".
 */
public class TagNotFoundException extends VcException {
  
  private static final long serialVersionUID = 1L;

  private static final String ERROR_CODE = "tag_not_found";

  /**
   * Constructor with tag name.
   *
   * @param tagName the name of the tag that was not found
   */
  public TagNotFoundException(String tagName) {
    super("Tag not found: " + tagName, ERROR_CODE, HttpStatus.NOT_FOUND);
  }
}
