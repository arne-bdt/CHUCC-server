package org.chucc.vcserver.exception;

/**
 * Thrown when attempting to change an immutable tag target.
 * Error code: tag_retarget_forbidden
 * HTTP status: 409 Conflict
 */
public class TagRetargetForbiddenException extends VcException {

  public TagRetargetForbiddenException(String message) {
    super(message, "tag_retarget_forbidden", 409);
  }

  public TagRetargetForbiddenException(String message, Throwable cause) {
    super(message, "tag_retarget_forbidden", 409, cause);
  }
}
