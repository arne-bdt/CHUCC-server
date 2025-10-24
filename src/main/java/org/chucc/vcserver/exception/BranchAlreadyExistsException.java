package org.chucc.vcserver.exception;

/**
 * Exception thrown when attempting to create a branch that already exists.
 */
public class BranchAlreadyExistsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new BranchAlreadyExistsException with the specified branch name.
   *
   * @param branchName the name of the branch that already exists
   */
  public BranchAlreadyExistsException(String branchName) {
    super("Branch already exists: " + branchName);
  }
}
