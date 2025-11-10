package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.chucc.vcserver.dto.CreateTagRequest;
import org.chucc.vcserver.dto.TagInfo;
import org.chucc.vcserver.dto.TagListResponse;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for tag pagination.
 * Tests with projector enabled to verify read-side behavior.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class TagPaginationIT extends ITFixture {

  private static final String DATASET_NAME = "test-tag-pagination";

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected String getDatasetName() {
    return DATASET_NAME;
  }

  @Test
  void listTags_withDefaultPagination_shouldReturnPaginatedResults() {
    // Arrange: Create 12 tags
    for (int i = 0; i < 12; i++) {
      createTag("v" + String.format("%03d", i));
    }

    // Act: Request with limit=10
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?limit=10",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(10);
    assertThat(body.pagination().offset()).isEqualTo(0);
    assertThat(body.pagination().limit()).isEqualTo(10);
    assertThat(body.pagination().hasMore()).isTrue();

    // Assert Link header
    List<String> linkHeaders = response.getHeaders().get("Link");
    assertThat(linkHeaders).hasSize(1);
    assertThat(linkHeaders.get(0))
        .contains("offset=10")
        .contains("limit=10")
        .contains("rel=\"next\"");
  }

  @Test
  void listTags_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 15 tags
    for (int i = 0; i < 15; i++) {
      createTag("v1.0." + i);
    }

    // Act: Get page 2 (offset=5, limit=5)
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?offset=5&limit=5",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).hasSize(5);
    assertThat(body.pagination().offset()).isEqualTo(5);
    assertThat(body.pagination().limit()).isEqualTo(5);
    assertThat(body.pagination().hasMore()).isTrue();
  }

  @Test
  void listTags_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total
    ResponseEntity<TagListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?offset=1000&limit=100",
        HttpMethod.GET,
        null,
        TagListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    TagListResponse body = response.getBody();
    assertThat(body.tags()).isEmpty();
    assertThat(body.pagination().hasMore()).isFalse();
  }

  @Test
  void listTags_limitExceedsMax_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?limit=2000",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listTags_negativeOffset_shouldReturn400() {
    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/tags?offset=-1",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private void createTag(String name) {
    CreateTagRequest request = new CreateTagRequest(
        name,
        initialCommitId.value(),
        "Tag " + name,
        "test-author"
    );
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<CreateTagRequest> httpEntity = new HttpEntity<>(request, headers);
    restTemplate.postForEntity(
        "/" + DATASET_NAME + "/version/tags",
        httpEntity,
        TagInfo.class
    );

    // Wait for async projection to complete
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var tag = tagRepository.findByDatasetAndName(DATASET_NAME, name);
          assertThat(tag).isPresent();
        });
  }
}
