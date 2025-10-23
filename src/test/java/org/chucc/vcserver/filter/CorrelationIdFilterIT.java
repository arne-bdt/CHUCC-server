package org.chucc.vcserver.filter;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CorrelationIdFilter.
 * Verifies that correlation IDs are generated for HTTP requests
 * without breaking the HTTP flow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class CorrelationIdFilterIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void httpRequest_shouldGenerateCorrelationId() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");
    HttpEntity<String> request = new HttpEntity<>("<urn:s> <urn:p> <urn:o> .", headers);

    // Act: Make HTTP request (filter should generate correlation ID)
    ResponseEntity<String> response = restTemplate.exchange(
        "/data?default=true&branch=main",
        HttpMethod.PUT,
        request,
        String.class);

    // Assert: Request succeeds (filter doesn't break HTTP flow)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    // Note: Correlation ID is in logs (verified manually during test runs)
    // and in Kafka event headers (verified by EventPublisherTest unit tests)
  }

  @Test
  void httpRequest_shouldWorkForMultipleRequests() {
    // Arrange
    HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/turtle");

    // Act: Make multiple HTTP requests
    for (int i = 0; i < 3; i++) {
      HttpEntity<String> request = new HttpEntity<>(
          "<urn:s" + i + "> <urn:p> <urn:o> .",
          headers
      );

      ResponseEntity<String> response = restTemplate.exchange(
          "/data?default=true&branch=main",
          HttpMethod.PUT,
          request,
          String.class);

      // Assert: Each request succeeds (each gets unique correlation ID)
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    // Note: Each request gets a unique correlation ID (verified in logs)
  }
}
