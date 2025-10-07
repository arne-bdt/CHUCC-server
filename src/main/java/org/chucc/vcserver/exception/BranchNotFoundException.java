package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested branch does not exist.
 * Maps to HTTP 404 Not Found with error code "branch_not_found".
 */
public class BranchNotFoundException extends VcException {

  private static final String ERROR_CODE = "branch_not_found";

  /**
   * Constructor with branch name.
   *
   * @param branchName the name of the branch that was not found
   */
  public BranchNotFoundException(String branchName) {
    super("Branch not found: " + branchName, ERROR_CODE, HttpStatus.NOT_FOUND.value());
  }
}
