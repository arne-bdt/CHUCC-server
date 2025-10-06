package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when tag deletion is forbidden by server policy.
 * Maps to HTTP 403 Forbidden with error code "tag_deletion_forbidden".
 */
public class TagDeletionForbiddenException extends VcException {

  private static final String ERROR_CODE = "tag_deletion_forbidden";

  /**
   * Default constructor.
   */
  public TagDeletionForbiddenException() {
    super("Tag deletion is disabled by server policy",
        ERROR_CODE, HttpStatus.FORBIDDEN.value());
  }
}
