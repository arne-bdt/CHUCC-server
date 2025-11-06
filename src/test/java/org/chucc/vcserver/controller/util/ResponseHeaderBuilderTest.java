package org.chucc.vcserver.controller.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Unit tests for {@link ResponseHeaderBuilder}.
 */
class ResponseHeaderBuilderTest {

  @Test
  void addContentLocation_shouldSetHeader() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, "/mydata/version/branches/main");

    assertThat(headers.getFirst("Content-Location"))
        .isEqualTo("/mydata/version/branches/main");
  }

  @Test
  void addContentLocation_shouldOverwriteExistingHeader() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, "/old/url");
    ResponseHeaderBuilder.addContentLocation(headers, "/new/url");

    assertThat(headers.getFirst("Content-Location"))
        .isEqualTo("/new/url");
  }

  @Test
  void addLink_shouldAddLinkHeader() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addLink(headers, "/mydata/version/branches/main", "branch");

    assertThat(headers.getFirst("Link"))
        .isEqualTo("</mydata/version/branches/main>; rel=\"branch\"");
  }

  @Test
  void addLink_shouldAllowMultipleLinks() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addLink(headers, "/url1", "branch");
    ResponseHeaderBuilder.addLink(headers, "/url2", "commit");

    assertThat(headers.get("Link"))
        .hasSize(2)
        .containsExactly(
            "</url1>; rel=\"branch\"",
            "</url2>; rel=\"commit\""
        );
  }

  @Test
  void addLink_shouldFormatWithAngleBracketsAndRelAttribute() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addLink(headers, "/mydata/version/commits/abc123", "version");

    assertThat(headers.getFirst("Link"))
        .startsWith("<")
        .endsWith(">; rel=\"version\"")
        .contains("/mydata/version/commits/abc123");
  }

  @Test
  void addCommitLink_shouldUseVersionControlUrls() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addCommitLink(headers, "mydata", "abc123");

    assertThat(headers.getFirst("Link"))
        .isEqualTo("</mydata/version/commits/abc123>; rel=\"version\"");
  }

  @Test
  void addBranchLink_shouldUseVersionControlUrls() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addBranchLink(headers, "mydata", "main");

    assertThat(headers.getFirst("Link"))
        .isEqualTo("</mydata/version/branches/main>; rel=\"branch\"");
  }

  @Test
  void addTagLink_shouldUseVersionControlUrls() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addTagLink(headers, "mydata", "v1.0");

    assertThat(headers.getFirst("Link"))
        .isEqualTo("</mydata/version/tags/v1.0>; rel=\"tag\"");
  }

  @Test
  void multipleConvenienceMethods_shouldAllowCombination() {
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addBranchLink(headers, "mydata", "main");
    ResponseHeaderBuilder.addCommitLink(headers, "mydata", "commit123");
    ResponseHeaderBuilder.addTagLink(headers, "mydata", "v1.0");

    assertThat(headers.get("Link"))
        .hasSize(3)
        .containsExactly(
            "</mydata/version/branches/main>; rel=\"branch\"",
            "</mydata/version/commits/commit123>; rel=\"version\"",
            "</mydata/version/tags/v1.0>; rel=\"tag\""
        );
  }
}
