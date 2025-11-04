package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests verifying HTTP 202 Accepted pattern for eventual consistency.
 *
 * <p>These tests verify that write operations return HTTP 202 Accepted
 * instead of HTTP 200 OK or 201 Created, reflecting the async nature
 * of the CQRS + Event Sourcing architecture.
 *
 * <p><strong>Tests with projector DISABLED (default):</strong>
 * <ul>
 *   <li>Verify HTTP 202 response status
 *   <li>Verify response headers (ETag, Location, SPARQL-VC-Status)
 *   <li>Verify data queryable via commit selector (uses event store directly)
 * </ul>
 *
 * <p><strong>Note:</strong> Tests requiring projector are in separate test class
 * {@link EventualConsistencyProjectorIT} to enable projector via {@code @TestPropertySource}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class EventualConsistencyIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  /**
   * Uses event-driven setup (Session 3 migration - proof of concept).
   */
  @Override
  protected void createInitialCommitAndBranch(String dataset) {
    createInitialCommitAndBranchViaEvents(dataset);
  }

  /**
   * Verifies that PUT /data returns HTTP 202 Accepted.
   */
  @Test
  void putGraph_shouldReturn202Accepted() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "text/turtle");

    // Act
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data?graph=http://example.org/graph1",
        HttpMethod.PUT,
        new HttpEntity<>(turtle, headers),
        Void.class
    );

    // Assert: HTTP 202 Accepted (not 200 OK)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Assert: Headers present
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getETag()).isNotNull();
    assertThat(response.getHeaders().getFirst("SPARQL-VC-Status"))
        .isEqualTo("pending");
  }

  /**
   * Verifies that multiple PUT operations return HTTP 202 Accepted.
   */
  @Test
  void multiplePutOperations_shouldReturn202Accepted() {
    // Act: Multiple PUT operations
    String turtle1 = "@prefix ex: <http://example.org/> . ex:s1 ex:p1 ex:o1 .";
    String turtle2 = "@prefix ex: <http://example.org/> . ex:s2 ex:p2 ex:o2 .";
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "text/turtle");

    // First PUT
    ResponseEntity<Void> response1 = restTemplate.exchange(
        "/data?graph=http://example.org/graph-a",
        HttpMethod.PUT,
        new HttpEntity<>(turtle1, headers),
        Void.class
    );

    // Second PUT
    ResponseEntity<Void> response2 = restTemplate.exchange(
        "/data?graph=http://example.org/graph-b",
        HttpMethod.PUT,
        new HttpEntity<>(turtle2, headers),
        Void.class
    );

    // Assert: Both return HTTP 202 Accepted
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response1.getHeaders().getFirst("SPARQL-VC-Status"))
        .isEqualTo("pending");

    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response2.getHeaders().getFirst("SPARQL-VC-Status"))
        .isEqualTo("pending");
  }

}
