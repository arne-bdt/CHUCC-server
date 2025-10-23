package org.chucc.vcserver.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing a commit identifier using UUIDv7 (time-based).
 * Immutable and ensures globally unique, time-ordered identifiers.
 */
public record CommitId(String value) {

  /**
   * Creates a new CommitId with the provided UUID string.
   *
   * @param value the UUID string (must be non-null and non-blank)
   * @throws IllegalArgumentException if value is null, blank, or not a valid UUID
   */
  public CommitId {
    Objects.requireNonNull(value, "CommitId value cannot be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("CommitId value cannot be blank");
    }
    try {
      UUID.fromString(value); // Validate UUID format
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("CommitId value must be a valid UUID: " + value, e);
    }
  }

  /**
   * Generates a new CommitId using UUIDv7 (time-based epoch generator).
   *
   * @return a new CommitId with a UUIDv7 identifier
   */
  public static CommitId generate() {
    UUID uuid = UuidCreator.getTimeOrderedEpoch();
    return new CommitId(uuid.toString());
  }

  /**
   * Creates a CommitId from an existing UUID string.
   *
   * @param uuid the UUID string
   * @return a new CommitId
   * @throws IllegalArgumentException if uuid is invalid
   */
  public static CommitId of(String uuid) {
    return new CommitId(uuid);
  }

  @Override
  public String toString() {
    return value;
  }
}
