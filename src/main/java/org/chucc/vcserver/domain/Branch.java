package org.chucc.vcserver.domain;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain entity representing a branch reference.
 * Branch names must be in Unicode NFC normalization and match the pattern ^[A-Za-z0-9._\-]+$.
 */
public final class Branch {
  private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

  private final String name;
  private CommitId commitId;

  /**
   * Creates a new Branch.
   *
   * @param name the branch name (must be non-null, non-blank, in NFC form, and match pattern)
   * @param commitId the commit this branch points to (must be non-null)
   * @throws IllegalArgumentException if validation fails
   */
  public Branch(String name, CommitId commitId) {
    Objects.requireNonNull(name, "Branch name cannot be null");
    Objects.requireNonNull(commitId, "Branch commitId cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }

    // Normalize to NFC form
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    if (!normalized.equals(name)) {
      throw new IllegalArgumentException(
          "Branch name must be in Unicode NFC normalization form: " + name);
    }

    // Validate against pattern
    if (!VALID_NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "Branch name must match pattern ^[A-Za-z0-9._\\-]+$: " + name);
    }

    this.name = name;
    this.commitId = commitId;
  }

  /**
   * Gets the branch name.
   *
   * @return the branch name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the commit ID this branch points to.
   *
   * @return the commit ID
   */
  public CommitId getCommitId() {
    return commitId;
  }

  /**
   * Updates the branch to point to a new commit (fast-forward or force update).
   *
   * @param newCommitId the new commit ID
   * @throws IllegalArgumentException if newCommitId is null
   */
  public void updateCommit(CommitId newCommitId) {
    Objects.requireNonNull(newCommitId, "New commitId cannot be null");
    this.commitId = newCommitId;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Branch branch = (Branch) obj;
    return Objects.equals(name, branch.name) && Objects.equals(commitId, branch.commitId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, commitId);
  }

  @Override
  public String toString() {
    return "Branch{name='" + name + "', commitId=" + commitId + "}";
  }
}
