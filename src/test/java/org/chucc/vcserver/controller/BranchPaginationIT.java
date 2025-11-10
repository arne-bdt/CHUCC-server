package org.chucc.vcserver.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.chucc.vcserver.dto.BranchListResponse;
import org.chucc.vcserver.dto.CreateBranchRequest;
import org.chucc.vcserver.dto.CreateBranchResponse;
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
 * Integration tests for branch pagination functionality.
 *
 * <p>Tests the GET /{dataset}/version/branches endpoint with pagination parameters:
 * <ul>
 *   <li>Default pagination (limit=100, offset=0)
 *   <li>Custom pagination (limit and offset parameters)
 *   <li>Offset beyond total results
 *   <li>Invalid limit (exceeds maximum)
 *   <li>Invalid offset (negative value)
 * </ul>
 *
 * <p><strong>Note:</strong> This test ENABLES the ReadModelProjector because
 * pagination requires branches to exist in the repository (read side).
 * Uses await() to handle async event projection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class BranchPaginationIT extends ITFixture {

  private static final String DATASET_NAME = "test-branch-pagination";

  @Autowired
  private TestRestTemplate restTemplate;

  @Override
  protected String getDatasetName() {
    return DATASET_NAME;
  }

  @Test
  void listBranches_withDefaultPagination_shouldReturnPaginatedResults() {
    // Arrange: Create 12 branches (tests pagination without overwhelming the system)
    for (int i = 0; i < 12; i++) {
      createBranch("branch-" + String.format("%02d", i));
    }

    // Act: Request with limit=10 (should return 10 of 13 total, +1 for main)
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/branches?limit=10",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert: Response status and pagination
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.branches()).hasSize(10);
    assertThat(body.pagination().offset()).isEqualTo(0);
    assertThat(body.pagination().limit()).isEqualTo(10);
    assertThat(body.pagination().hasMore()).isTrue();

    // Assert: Link header for next page
    List<String> linkHeaders = response.getHeaders().get("Link");
    assertThat(linkHeaders).isNotNull().hasSize(1);
    assertThat(linkHeaders.get(0))
        .contains("offset=10")
        .contains("limit=10")
        .contains("rel=\"next\"");

    // Assert: Content-Location header
    assertThat(response.getHeaders().getFirst("Content-Location"))
        .isEqualTo("/" + DATASET_NAME + "/version/branches");
  }

  @Test
  void listBranches_withCustomPagination_shouldReturnCorrectPage() {
    // Arrange: Create 15 branches
    for (int i = 0; i < 15; i++) {
      createBranch("branch-" + String.format("%02d", i));
    }

    // Act: Get second page (offset=5, limit=5)
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/branches?offset=5&limit=5",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.branches()).hasSize(5);
    assertThat(body.pagination().offset()).isEqualTo(5);
    assertThat(body.pagination().limit()).isEqualTo(5);
    assertThat(body.pagination().hasMore()).isTrue();
  }

  @Test
  void listBranches_offsetBeyondTotal_shouldReturnEmptyList() {
    // Act: Offset beyond total (only main branch exists by default)
    ResponseEntity<BranchListResponse> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/branches?offset=1000&limit=100",
        HttpMethod.GET,
        null,
        BranchListResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    BranchListResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.branches()).isEmpty();
    assertThat(body.pagination().hasMore()).isFalse();

    // Assert: No Link header (no next page)
    assertThat(response.getHeaders().get("Link")).isNullOrEmpty();
  }

  @Test
  void listBranches_limitExceedsMax_shouldReturn400() {
    // Act: Request with limit > MAX_LIMIT (1000)
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/branches?limit=2000",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody())
        .contains("Limit must be between 1 and 1000");
  }

  @Test
  void listBranches_negativeOffset_shouldReturn400() {
    // Act: Request with negative offset
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + DATASET_NAME + "/version/branches?offset=-1",
        HttpMethod.GET,
        null,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody())
        .contains("Offset cannot be negative");
  }

  /**
   * Helper method to create a branch via REST API and wait for projection.
   *
   * @param name the branch name
   */
  private void createBranch(String name) {
    CreateBranchRequest request = new CreateBranchRequest(name, "main", false);
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Author", "TestUser <test@example.org>");
    HttpEntity<CreateBranchRequest> httpEntity = new HttpEntity<>(request, headers);
    restTemplate.postForEntity(
        "/" + DATASET_NAME + "/version/branches",
        httpEntity,
        CreateBranchResponse.class
    );

    // Wait for async projection to complete
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var branch = branchRepository.findByDatasetAndName(DATASET_NAME, name);
          assertThat(branch).isPresent();
        });
  }
}
