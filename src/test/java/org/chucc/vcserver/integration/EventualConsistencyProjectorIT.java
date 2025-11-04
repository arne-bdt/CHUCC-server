package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests verifying eventual consistency with projector enabled.
 *
 * <p>These tests verify that data eventually becomes queryable via branch selector
 * after async projection completes. The ReadModelProjector is enabled for these tests
 * via {@code @TestPropertySource(properties = "projector.kafka-listener.enabled=true")}.
 *
 * <p>These tests use {@code await()} to wait for async projection to complete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class EventualConsistencyProjectorIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  /**
   * Verifies eventual consistency when projector is enabled.
   * Data becomes queryable via branch selector after async projection.
   */
  @Test
  void putGraph_eventuallyUpdatesReadModel() {
    // Arrange
    String turtle = "@prefix ex: <http://example.org/> . ex:s ex:p ex:o .";
    HttpHeaders putHeaders = new HttpHeaders();
    putHeaders.set(HttpHeaders.CONTENT_TYPE, "text/turtle");

    // Act: PUT graph (using unique graph name to avoid conflicts)
    String graphUri = "http://example.org/projector-test-" + System.currentTimeMillis();
    ResponseEntity<Void> putResponse = restTemplate.exchange(
        "/data?graph=" + graphUri,
        HttpMethod.PUT,
        new HttpEntity<>(turtle, putHeaders),
        Void.class
    );

    // Assert: HTTP 202 Accepted
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Assert: Eventually queryable via branch selector (uses read model)
    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.set(HttpHeaders.ACCEPT, "text/turtle");
    await().atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> {
          ResponseEntity<String> getResponse = restTemplate.exchange(
              "/data?graph=" + graphUri + "&branch=main",
              HttpMethod.GET,
              new HttpEntity<>(getHeaders),
              String.class
          );
          assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
          assertThat(getResponse.getBody()).contains("http://example.org/s");
        });
  }
}
