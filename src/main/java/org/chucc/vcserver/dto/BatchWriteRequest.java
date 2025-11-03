package org.chucc.vcserver.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Request DTO for batch write operations endpoint.
 *
 * <p>Combines multiple write operations (SPARQL updates or RDF patches) into a single commit.
 * All operations must target the same branch.
 *
 * <p>The branch can be specified either at the request level (applies to all operations)
 * or per-operation. If both are specified, the operation-level branch takes precedence,
 * but all operations must ultimately target the same branch.
 *
 * @param operations List of write operations to execute
 * @param branch Default branch for all operations (can be overridden per operation)
 */
public record BatchWriteRequest(
    List<WriteOperation> operations,
    String branch
) {
  /**
   * Compact constructor with defensive copying for immutability.
   *
   * @param operations List of write operations (defensively copied)
   * @param branch Default branch name
   */
  public BatchWriteRequest {
    // Defensive copy to prevent external modification
    operations = operations != null
        ? Collections.unmodifiableList(new ArrayList<>(operations))
        : List.of();
  }

  /**
   * Returns unmodifiable list of operations.
   *
   * @return unmodifiable list (safe to return as it's already immutable)
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Returns unmodifiable list created in constructor")
  @Override
  public List<WriteOperation> operations() {
    return operations;
  }

  /**
   * Validates the batch request.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("Operations list cannot be empty");
    }

    // Validate individual operations
    for (int i = 0; i < operations.size(); i++) {
      try {
        operations.get(i).validate();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Invalid operation at index " + i + ": " + e.getMessage(), e);
      }
    }

    // Ensure all operations target same branch
    String targetBranch = determineTargetBranch();
    for (int i = 0; i < operations.size(); i++) {
      WriteOperation op = operations.get(i);
      String opBranch = op.branch() != null ? op.branch() : targetBranch;
      if (!opBranch.equals(targetBranch)) {
        throw new IllegalArgumentException(
            "All operations must target the same branch. Operation " + i
            + " targets '" + opBranch + "' but expected '" + targetBranch + "'");
      }
    }
  }

  /**
   * Determines the target branch (from request or first operation).
   *
   * @return target branch name
   */
  public String determineTargetBranch() {
    if (branch != null) {
      return branch;
    }
    if (!operations.isEmpty() && operations.get(0).branch() != null) {
      return operations.get(0).branch();
    }
    return "main";  // Default
  }
}
