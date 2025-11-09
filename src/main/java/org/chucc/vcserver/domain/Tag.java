package org.chucc.vcserver.domain;

import java.time.Instant;
import java.util.Objects;
import org.chucc.vcserver.util.IdentifierValidator;

/**
 * Domain entity representing an immutable tag reference.
 * Tag names must be in Unicode NFC normalization and match the pattern ^[A-Za-z0-9._\-]+$.
 * Once created, a tag cannot be modified - it permanently points to a specific commit.
 */
public record Tag(
    String name,
    CommitId commitId,
    String message,
    String author,
    Instant createdAt) {

  /**
   * Creates a new immutable Tag with all fields.
   *
   * @param name the tag name (must be non-null, non-blank, in NFC form, and match pattern)
   * @param commitId the commit this tag points to (must be non-null)
   * @param message optional annotation message
   * @param author tag author
   * @param createdAt tag creation timestamp (must be non-null)
   * @throws IllegalArgumentException if validation fails
   */
  public Tag {
    Objects.requireNonNull(commitId, "Tag commitId cannot be null");
    Objects.requireNonNull(createdAt, "Tag createdAt cannot be null");

    // Validate name using shared validator
    IdentifierValidator.validate(name, IdentifierValidator.MAX_BRANCH_TAG_NAME_LENGTH, "Tag");
  }

  /**
   * Convenience constructor for tags without message and author.
   *
   * @param name the tag name
   * @param commitId the commit this tag points to
   */
  public Tag(String name, CommitId commitId) {
    this(name, commitId, null, null, Instant.now());
  }

  @Override
  public String toString() {
    return "Tag{name='" + name + "', commitId=" + commitId
        + ", message='" + message + "', author='" + author
        + "', createdAt=" + createdAt + "}";
  }
}
