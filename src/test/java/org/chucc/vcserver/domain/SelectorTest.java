package org.chucc.vcserver.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.chucc.vcserver.exception.SelectorConflictException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Selector value object.
 */
class SelectorTest {

  @Test
  void testCreateWithOnlyBranch() {
    Selector selector = assertDoesNotThrow(() -> Selector.of("main", null, null));
    assertNotNull(selector);
    assertTrue(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertFalse(selector.hasAsOf());
    assertEquals("main", selector.getBranch());
    assertNull(selector.getCommit());
    assertNull(selector.getAsOf());
  }

  @Test
  void testCreateWithOnlyCommit() {
    Selector selector = assertDoesNotThrow(() -> Selector.of(null, "abc123", null));
    assertNotNull(selector);
    assertFalse(selector.hasBranch());
    assertTrue(selector.hasCommit());
    assertFalse(selector.hasAsOf());
    assertNull(selector.getBranch());
    assertEquals("abc123", selector.getCommit());
    assertNull(selector.getAsOf());
  }

  @Test
  void testCreateWithOnlyAsOf() {
    String timestamp = "2024-01-01T00:00:00Z";
    Selector selector = assertDoesNotThrow(() -> Selector.of(null, null, timestamp));
    assertNotNull(selector);
    assertFalse(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertTrue(selector.hasAsOf());
    assertNull(selector.getBranch());
    assertNull(selector.getCommit());
    assertEquals(timestamp, selector.getAsOf());
  }

  @Test
  void testCreateWithBranchAndAsOf() {
    String timestamp = "2024-01-01T00:00:00Z";
    Selector selector = assertDoesNotThrow(() -> Selector.of("main", null, timestamp));
    assertNotNull(selector);
    assertTrue(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertTrue(selector.hasAsOf());
    assertEquals("main", selector.getBranch());
    assertNull(selector.getCommit());
    assertEquals(timestamp, selector.getAsOf());
  }

  @Test
  void testCreateWithAllNull() {
    Selector selector = assertDoesNotThrow(() -> Selector.of(null, null, null));
    assertNotNull(selector);
    assertFalse(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertFalse(selector.hasAsOf());
    assertNull(selector.getBranch());
    assertNull(selector.getCommit());
    assertNull(selector.getAsOf());
  }

  @Test
  void testInvalidBranchAndCommit() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> Selector.of("main", "abc123", null));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testInvalidCommitAndAsOf() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> Selector.of(null, "abc123", "2024-01-01T00:00:00Z"));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testInvalidAllThree() {
    SelectorConflictException exception = assertThrows(
        SelectorConflictException.class,
        () -> Selector.of("main", "abc123", "2024-01-01T00:00:00Z"));
    assertEquals("selector_conflict", exception.getCode());
    assertEquals(400, exception.getStatus());
  }

  @Test
  void testEmptyStringsAreTreatedAsNull() {
    Selector selector = assertDoesNotThrow(() -> Selector.of("", "", ""));
    assertNotNull(selector);
    assertFalse(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertFalse(selector.hasAsOf());
  }

  @Test
  void testBlankStringsAreTreatedAsNull() {
    Selector selector = assertDoesNotThrow(() -> Selector.of("  ", "  ", "  "));
    assertNotNull(selector);
    assertFalse(selector.hasBranch());
    assertFalse(selector.hasCommit());
    assertFalse(selector.hasAsOf());
  }
}
