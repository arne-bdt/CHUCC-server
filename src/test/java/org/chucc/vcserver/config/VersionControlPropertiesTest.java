package org.chucc.vcserver.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VersionControlProperties configuration.
 */
class VersionControlPropertiesTest {

  @Test
  void defaultLevel_isLevel2() {
    VersionControlProperties props = new VersionControlProperties();
    assertEquals(2, props.getLevel());
  }

  @Test
  void level1_disablesLevel2Features() {
    VersionControlProperties props = new VersionControlProperties();
    props.setLevel(1);

    assertEquals(1, props.getLevel());

    // Level 1 features should be enabled
    assertTrue(props.isCommitsEnabled());
    assertTrue(props.isBranchesEnabled());
    assertTrue(props.isHistoryEnabled());
    assertTrue(props.isRdfPatchEnabled());

    // Level 2 features should be disabled
    assertFalse(props.isMergeEnabled());
    assertFalse(props.isConflictDetectionEnabled());
    assertFalse(props.isTagsEnabled());
    assertFalse(props.isRevertEnabled());
    assertFalse(props.isResetEnabled());
    assertFalse(props.isCherryPickEnabled());
    assertFalse(props.isBlameEnabled());
  }

  @Test
  void level2_enablesAllFeatures() {
    VersionControlProperties props = new VersionControlProperties();
    props.setLevel(2);

    assertEquals(2, props.getLevel());

    // All features should be enabled by default
    assertTrue(props.isCommitsEnabled());
    assertTrue(props.isBranchesEnabled());
    assertTrue(props.isHistoryEnabled());
    assertTrue(props.isRdfPatchEnabled());
    assertTrue(props.isMergeEnabled());
    assertTrue(props.isConflictDetectionEnabled());
    assertTrue(props.isTagsEnabled());
    assertTrue(props.isRevertEnabled());
    assertTrue(props.isResetEnabled());
    assertTrue(props.isCherryPickEnabled());
    assertTrue(props.isBlameEnabled());
  }

  @Test
  void invalidLevel_throwsException() {
    VersionControlProperties props = new VersionControlProperties();

    assertThrows(IllegalArgumentException.class, () -> props.setLevel(0));
    assertThrows(IllegalArgumentException.class, () -> props.setLevel(3));
    assertThrows(IllegalArgumentException.class, () -> props.setLevel(-1));
  }

  @Test
  void level1Features_respectIndividualToggles() {
    VersionControlProperties props = new VersionControlProperties();
    props.setLevel(1);

    props.setCommitsEnabled(false);
    assertFalse(props.isCommitsEnabled());

    props.setBranchesEnabled(false);
    assertFalse(props.isBranchesEnabled());
  }

  @Test
  void level2Features_requireLevel2() {
    VersionControlProperties props = new VersionControlProperties();
    props.setLevel(1);

    // Even if manually enabled, Level 2 features should be disabled on Level 1
    props.setMergeEnabled(true);
    assertFalse(props.isMergeEnabled(), "Merge should be disabled on Level 1");

    props.setTagsEnabled(true);
    assertFalse(props.isTagsEnabled(), "Tags should be disabled on Level 1");
  }

  @Test
  void level2Features_canBeIndividuallyDisabled() {
    VersionControlProperties props = new VersionControlProperties();
    props.setLevel(2);

    props.setMergeEnabled(false);
    assertFalse(props.isMergeEnabled());

    props.setTagsEnabled(false);
    assertFalse(props.isTagsEnabled());

    // Others should remain enabled
    assertTrue(props.isRevertEnabled());
    assertTrue(props.isBlameEnabled());
  }
}
