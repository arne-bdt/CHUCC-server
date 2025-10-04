package org.chucc.vcserver.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain entity representing a commit in the version control system.
 * Contains metadata about the commit including parents, author, message, and timestamp.
 */
public record Commit(
    CommitId id,
    List<CommitId> parents,
    String author,
    String message,
    Instant timestamp) {

  /**
   * Creates a new Commit with validation.
   *
   * @param id the commit identifier (must be non-null)
   * @param parents the list of parent commit IDs (must be non-null, can be empty)
   * @param author the commit author (must be non-null and non-blank)
   * @param message the commit message (must be non-null and non-blank)
   * @param timestamp the commit timestamp (must be non-null)
   * @throws IllegalArgumentException if any validation fails
   */
  public Commit {
    Objects.requireNonNull(id, "Commit id cannot be null");
    Objects.requireNonNull(parents, "Commit parents cannot be null");
    Objects.requireNonNull(author, "Commit author cannot be null");
    Objects.requireNonNull(message, "Commit message cannot be null");
    Objects.requireNonNull(timestamp, "Commit timestamp cannot be null");

    if (author.isBlank()) {
      throw new IllegalArgumentException("Commit author cannot be blank");
    }
    if (message.isBlank()) {
      throw new IllegalArgumentException("Commit message cannot be blank");
    }

    // Create defensive copy of parents list to ensure immutability
    parents = List.copyOf(parents);
  }

  /**
   * Creates a new commit with generated ID and current timestamp.
   *
   * @param parents the list of parent commit IDs
   * @param author the commit author
   * @param message the commit message
   * @return a new Commit
   */
  public static Commit create(List<CommitId> parents, String author, String message) {
    return new Commit(
        CommitId.generate(),
        parents,
        author,
        message,
        Instant.now()
    );
  }

  /**
   * Checks if this is an initial commit (no parents).
   *
   * @return true if this commit has no parents
   */
  public boolean isInitial() {
    return parents.isEmpty();
  }

  /**
   * Checks if this is a merge commit (more than one parent).
   *
   * @return true if this commit has more than one parent
   */
  public boolean isMerge() {
    return parents.size() > 1;
  }
}
