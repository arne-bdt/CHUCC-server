package org.chucc.vcserver.domain;

import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Domain entity representing an immutable tag reference.
 * Tag names must be in Unicode NFC normalization and match the pattern ^[A-Za-z0-9._\-]+$.
 * Once created, a tag cannot be modified - it permanently points to a specific commit.
 */
public record Tag(String name, CommitId commitId) {
  private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-]+$");

  /**
   * Creates a new immutable Tag.
   *
   * @param name the tag name (must be non-null, non-blank, in NFC form, and match pattern)
   * @param commitId the commit this tag points to (must be non-null)
   * @throws IllegalArgumentException if validation fails
   */
  public Tag {
    Objects.requireNonNull(name, "Tag name cannot be null");
    Objects.requireNonNull(commitId, "Tag commitId cannot be null");

    if (name.isBlank()) {
      throw new IllegalArgumentException("Tag name cannot be blank");
    }

    // Normalize to NFC form
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    if (!normalized.equals(name)) {
      throw new IllegalArgumentException(
          "Tag name must be in Unicode NFC normalization form: " + name);
    }

    // Validate against pattern
    if (!VALID_NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "Tag name must match pattern ^[A-Za-z0-9._\\-]+$: " + name);
    }
  }

  @Override
  public String toString() {
    return "Tag{name='" + name + "', commitId=" + commitId + "}";
  }
}
