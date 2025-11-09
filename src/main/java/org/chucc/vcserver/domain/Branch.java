package org.chucc.vcserver.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain entity representing a branch reference.
 * Branch names must be in Unicode NFC normalization and match the pattern ^[A-Za-z0-9._\-]+$.
 * Includes metadata for Git-like branch management: protection status, timestamps, commit count.
 */
public final class Branch {
  private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

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
    Objects.requireNonNull(name, "Branch name cannot be null");
    Objects.requireNonNull(commitId, "Branch commitId cannot be null");
    Objects.requireNonNull(createdAt, "createdAt cannot be null");
    Objects.requireNonNull(lastUpdated, "lastUpdated cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Branch name cannot be blank");
    }

    if (commitCount < 1) {
      throw new IllegalArgumentException("commitCount must be at least 1");
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

    // Validate against reserved names
    if (name.equals(".") || name.equals("..")) {
      throw new IllegalArgumentException("Branch name cannot be '.' or '..'");
    }

    // Reject names starting with underscore (reserved for internal use)
    if (name.startsWith("_")) {
      throw new IllegalArgumentException(
          "Branch name cannot start with '_' (reserved for internal use): " + name);
    }

    // Reject names starting or ending with dot
    if (name.startsWith(".")) {
      throw new IllegalArgumentException("Branch name cannot start with '.': " + name);
    }
    if (name.endsWith(".")) {
      throw new IllegalArgumentException("Branch name cannot end with '.': " + name);
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
