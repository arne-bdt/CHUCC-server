package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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
 * Integration tests for batch operations with SPARQL updates (projector enabled).
 *
 * <p>These tests verify batch operations that use SPARQL updates, which require
 * dataset materialization from the repository. The ReadModelProjector must be enabled
 * for these tests to populate the repository from events.
 *
 * <p>This test class complements BatchOperationsIT by testing scenarios that require
 * the projector to be running. Tests in BatchOperationsIT use only RDF patches and
 * run with the projector disabled (API layer testing pattern).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
public class BatchOperationsProjectorIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates() throws Exception {
    // Given: Batch with multiple SPARQL updates
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            ),
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s2> <http://ex.org/p> \"v2\" }"
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Batch insert test data");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then: API response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().get("Location")).isNotNull();
    assertThat(response.getHeaders().get("SPARQL-VC-Status")).contains("pending");

    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("status")).isEqualTo("accepted");
    assertThat(body.get("commitId")).isNotNull();
    assertThat(body.get("branch")).isEqualTo("main");
    assertThat(body.get("operationCount")).isEqualTo(2);
    assertThat(body.get("message")).isEqualTo("Batch insert test data");

    // Wait for async projection
    String commitId = (String) body.get("commitId");
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId("default",
              org.chucc.vcserver.domain.CommitId.of(commitId));
          assertThat(commit).isPresent();
        });
  }

  @Test
  void batchUpdate_shouldCreateSingleCommit_whenMixedOperations() throws Exception {
    // Given: Batch with both SPARQL update and RDF patch
    String patch = "TX .\n"
        + "A <http://ex.org/s2> <http://ex.org/p> \"v2\" .\n"
        + "TC .";

    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }"
            ),
            Map.of(
                "type", "applyPatch",
                "patch", patch
            )
        ),
        "branch", "main"
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Mixed batch operations");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then: API response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("operationCount")).isEqualTo(2);

    // Wait for async projection
    String commitId = (String) body.get("commitId");
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId("default",
              org.chucc.vcserver.domain.CommitId.of(commitId));
          assertThat(commit).isPresent();
        });
  }

  @Test
  void batchUpdate_shouldUseBranchFromOperations_whenRequestLevelBranchNotProvided()
      throws Exception {
    // Given: No request-level branch, operations specify branch
    Map<String, Object> request = Map.of(
        "operations", List.of(
            Map.of(
                "type", "update",
                "sparql", "INSERT DATA { <http://ex.org/s1> <http://ex.org/p> \"v1\" }",
                "branch", "main"
            )
        )
        // No request-level branch
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("SPARQL-VC-Message", "Test message");
    headers.set("SPARQL-VC-Author", "testUser");

    HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(request, headers);

    // When
    @SuppressWarnings("rawtypes")
    ResponseEntity<Map> response = restTemplate.exchange(
        "/version/batch?dataset=default",
        HttpMethod.POST,
        httpRequest,
        Map.class
    );

    // Then: API response
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    @SuppressWarnings("unchecked")
    Map<String, Object> body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("branch")).isEqualTo("main");

    // Wait for async projection
    String commitId = (String) body.get("commitId");
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          var commit = commitRepository.findByDatasetAndId("default",
              org.chucc.vcserver.domain.CommitId.of(commitId));
          assertThat(commit).isPresent();
        });
  }
}
