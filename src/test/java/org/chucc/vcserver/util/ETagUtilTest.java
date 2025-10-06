package org.chucc.vcserver.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EtagUtil}.
 */
class EtagUtilTest {

  @Test
  void createStrongEtag_shouldWrapCommitIdInQuotes() {
    String commitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String etag = EtagUtil.createStrongEtag(commitId);
    assertThat(etag).isEqualTo("\"01936c7f-8a2e-7890-abcd-ef1234567890\"");
  }

  @Test
  void parseEtag_shouldRemoveQuotes() {
    String etag = "\"01936c7f-8a2e-7890-abcd-ef1234567890\"";
    String commitId = EtagUtil.parseEtag(etag);
    assertThat(commitId).isEqualTo("01936c7f-8a2e-7890-abcd-ef1234567890");
  }

  @Test
  void parseEtag_shouldHandleETagWithoutQuotes() {
    String etag = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String commitId = EtagUtil.parseEtag(etag);
    assertThat(commitId).isEqualTo("01936c7f-8a2e-7890-abcd-ef1234567890");
  }

  @Test
  void parseEtag_shouldReturnNullForNullInput() {
    String commitId = EtagUtil.parseEtag(null);
    assertThat(commitId).isNull();
  }

  @Test
  void matches_shouldReturnTrueWhenETagMatchesCommitId() {
    String etag = "\"01936c7f-8a2e-7890-abcd-ef1234567890\"";
    String commitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    assertThat(EtagUtil.matches(etag, commitId)).isTrue();
  }

  @Test
  void matches_shouldReturnFalseWhenETagDoesNotMatchCommitId() {
    String etag = "\"01936c7f-8a2e-7890-abcd-ef1234567890\"";
    String commitId = "01936c7f-8a2e-7890-abcd-different";
    assertThat(EtagUtil.matches(etag, commitId)).isFalse();
  }

  @Test
  void matches_shouldReturnFalseWhenETagIsNull() {
    assertThat(EtagUtil.matches(null, "01936c7f-8a2e-7890-abcd-ef1234567890")).isFalse();
  }

  @Test
  void matches_shouldHandleETagWithoutQuotes() {
    String etag = "01936c7f-8a2e-7890-abcd-ef1234567890";
    String commitId = "01936c7f-8a2e-7890-abcd-ef1234567890";
    assertThat(EtagUtil.matches(etag, commitId)).isTrue();
  }
}
