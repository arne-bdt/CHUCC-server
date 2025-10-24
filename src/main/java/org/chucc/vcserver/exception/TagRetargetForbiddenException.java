package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when attempting to change an immutable tag target.
 * Error code: tag_retarget_forbidden
 * HTTP status: 409 Conflict
 */
public class TagRetargetForbiddenException extends VcException {
  
  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new TagRetargetForbiddenException with the specified message.
   *
   * @param message the detail message
   */
  public TagRetargetForbiddenException(String message) {
    super(message, "tag_retarget_forbidden", HttpStatus.CONFLICT);
  }

  /**
   * Constructs a new TagRetargetForbiddenException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the cause of this exception
   */
  public TagRetargetForbiddenException(String message, Throwable cause) {
    super(message, "tag_retarget_forbidden", HttpStatus.CONFLICT, cause);
  }
}
