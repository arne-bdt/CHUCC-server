package org.chucc.vcserver.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommitIdTest {

  @Test
  void testGenerateCreatesValidUuidv7() {
    CommitId commitId = CommitId.generate();
    assertNotNull(commitId);
    assertNotNull(commitId.value());

    // Verify it's a valid UUID
    assertDoesNotThrow(() -> UUID.fromString(commitId.value()));
  }

  @Test
  void testGenerateCreatesUniqueIds() {
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      CommitId commitId = CommitId.generate();
      ids.add(commitId.value());
    }

    // All generated IDs should be unique
    assertEquals(1000, ids.size());
  }

  @Test
  void testOfWithValidUuid() {
    String validUuid = "550e8400-e29b-41d4-a716-446655440000";
    CommitId commitId = CommitId.of(validUuid);

    assertNotNull(commitId);
    assertEquals(validUuid, commitId.value());
  }

  @Test
  void testConstructorWithValidUuid() {
    String validUuid = "550e8400-e29b-41d4-a716-446655440000";
    CommitId commitId = new CommitId(validUuid);

    assertNotNull(commitId);
    assertEquals(validUuid, commitId.value());
  }

  @Test
  void testConstructorWithNullThrowsException() {
    assertThrows(NullPointerException.class, () -> new CommitId(null));
  }

  @Test
  void testConstructorWithBlankThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new CommitId(""));
    assertThrows(IllegalArgumentException.class, () -> new CommitId("   "));
  }

  @Test
  void testConstructorWithInvalidUuidThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new CommitId("not-a-uuid"));
    assertThrows(IllegalArgumentException.class, () -> new CommitId("12345"));
    assertThrows(IllegalArgumentException.class,
        () -> new CommitId("550e8400-e29b-41d4-a716")); // Incomplete UUID
  }

  @Test
  void testToString() {
    String validUuid = "550e8400-e29b-41d4-a716-446655440000";
    CommitId commitId = new CommitId(validUuid);

    assertEquals(validUuid, commitId.toString());
  }

  @Test
  void testEquality() {
    String uuid1 = "550e8400-e29b-41d4-a716-446655440000";
    String uuid2 = "550e8400-e29b-41d4-a716-446655440001";

    CommitId commitId1a = new CommitId(uuid1);
    CommitId commitId1b = new CommitId(uuid1);
    CommitId commitId2 = new CommitId(uuid2);

    assertEquals(commitId1a, commitId1b);
    assertNotEquals(commitId1a, commitId2);
  }

  @Test
  void testHashCode() {
    String uuid1 = "550e8400-e29b-41d4-a716-446655440000";
    String uuid2 = "550e8400-e29b-41d4-a716-446655440001";

    CommitId commitId1a = new CommitId(uuid1);
    CommitId commitId1b = new CommitId(uuid1);
    CommitId commitId2 = new CommitId(uuid2);

    assertEquals(commitId1a.hashCode(), commitId1b.hashCode());
    assertNotEquals(commitId1a.hashCode(), commitId2.hashCode());
  }

  @Test
  void testGeneratedIdsAreTimeBased() throws InterruptedException {
    CommitId id1 = CommitId.generate();
    Thread.sleep(10); // Small delay to ensure different timestamps
    CommitId id2 = CommitId.generate();

    // Both should be valid UUIDs
    assertNotNull(id1.value());
    assertNotNull(id2.value());

    // They should be different
    assertNotEquals(id1, id2);

    // UUIDv7 should maintain time ordering (lexicographically sortable)
    assertTrue(id1.value().compareTo(id2.value()) < 0,
        "UUIDv7 should be time-ordered: " + id1.value() + " should be < " + id2.value());
  }
}
