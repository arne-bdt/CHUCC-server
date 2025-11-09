package org.chucc.vcserver.domain;

import java.time.Instant;
import java.util.Objects;
import org.chucc.vcserver.util.IdentifierValidator;

/**
 * Domain entity representing a branch reference.
 * Branch names must be in Unicode NFC normalization and match the pattern ^[A-Za-z0-9._\-]+$.
 * Includes metadata for Git-like branch management: protection status, timestamps, commit count.
 */
public final class Branch {

  private final String name;
  private CommitId commitId;
  private final boolean isProtected;
  private final Instant createdAt;
  private Instant lastUpdated;
  private int commitCount;

  /**
   * Creates a new Branch with full metadata.
   *
   * @param name the branch name (must be non-null, non-blank, in NFC form, and match pattern)
   * @param commitId the commit this branch points to (must be non-null)
   * @param isProtected whether this branch is protected from deletion/force-push
   * @param createdAt the creation timestamp (must be non-null)
   * @param lastUpdated the last update timestamp (must be non-null)
   * @param commitCount the number of commits on this branch (must be >= 1)
   * @throws IllegalArgumentException if validation fails
   */
  public Branch(String name, CommitId commitId, boolean isProtected,
                Instant createdAt, Instant lastUpdated, int commitCount) {
    Objects.requireNonNull(commitId, "Branch commitId cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");

    // Validate name using shared validator
    IdentifierValidator.validate(name, IdentifierValidator.MAX_BRANCH_TAG_NAME_LENGTH, "Branch");

    if (commitCount < 1) {
      throw new IllegalArgumentException("commitCount must be at least 1");
    }

    this.name = name;
    this.commitId = commitId;
    this.isProtected = isProtected;
    this.createdAt = createdAt;
    this.lastUpdated = lastUpdated;
    this.commitCount = commitCount;
  }

  /**
   * Creates a new Branch with default metadata (for tests/convenience).
   * Default: not protected, current timestamp, commit count = 1.
   *
   * @param name the branch name
   * @param commitId the commit this branch points to
   */
  public Branch(String name, CommitId commitId) {
    this(name, commitId, false, Instant.now(), Instant.now(), 1);
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
   * Checks if this branch is protected from deletion/force-push.
   *
   * @return true if branch is protected
   */
  public boolean isProtected() {
    return isProtected;
  }

  /**
   * Gets the creation timestamp.
   *
   * @return the creation timestamp
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Gets the last update timestamp.
   *
   * @return the last update timestamp
   */
  public Instant getLastUpdated() {
    return lastUpdated;
  }

  /**
   * Gets the total number of commits on this branch.
   *
   * @return the commit count
   */
  public int getCommitCount() {
    return commitCount;
  }

  /**
   * Updates the branch to point to a new commit (fast-forward or force update).
   * Also updates lastUpdated timestamp and increments commit count.
   *
   * @param newCommitId the new commit ID
   * @throws IllegalArgumentException if newCommitId is null
   */
  public void updateCommit(CommitId newCommitId) {
    Objects.requireNonNull(newCommitId, "New commitId cannot be null");
    this.commitId = newCommitId;
    this.lastUpdated = Instant.now();
    this.commitCount++;
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
    return Objects.equals(name, branch.name)
        && Objects.equals(commitId, branch.commitId)
        && isProtected == branch.isProtected
        && Objects.equals(createdAt, branch.createdAt)
        && Objects.equals(lastUpdated, branch.lastUpdated)
        && commitCount == branch.commitCount;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, commitId, isProtected, createdAt, lastUpdated, commitCount);
  }

  @Override
  public String toString() {
    return "Branch{name='" + name + "', commitId=" + commitId
        + ", isProtected=" + isProtected + ", commitCount=" + commitCount + "}";
  }
}
