package org.chucc.vcserver.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chucc.vcserver.testutil.KafkaTestContainers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for selector validation.
 * Verifies that conflicting selectors return 400 Bad Request with problem+json.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SelectorValidationIntegrationTest {

  private static KafkaContainer kafkaContainer;

  @Autowired
  private TestRestTemplate restTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeAll
  static void startKafka() {
    kafkaContainer = KafkaTestContainers.createKafkaContainer();
    // Container is started by KafkaTestContainers - shared across all tests
  }

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
  }

  @Test
  void testBranchAndCommitConflict_returns400() throws Exception {
    // When: query with both branch and commit selectors
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&branch=main&commit=abc123";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
    assertThat(json.has("type")).isTrue();
    assertThat(json.has("title")).isTrue();
  }

  @Test
  void testCommitAndAsOfConflict_returns400() throws Exception {
    // When: query with both commit and asOf selectors
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&commit=abc123&asOf=2024-01-01T00:00:00Z";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
  }

  @Test
  void testAllThreeSelectorsConflict_returns400() throws Exception {
    // When: query with all three selectors
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&branch=main&commit=abc123&asOf=2024-01-01T00:00:00Z";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should return 400 with problem+json
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/problem+json"));

    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("status").asInt()).isEqualTo(400);
    assertThat(json.get("code").asText()).isEqualTo("selector_conflict");
  }

  @Test
  void testBranchAndAsOfCombination_isAllowed() throws Exception {
    // When: query with branch and asOf selectors (special case)
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&branch=main&asOf=2024-01-01T00:00:00Z";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should not return 400 (this combination is allowed)
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleBranchSelector_isAllowed() throws Exception {
    // When: query with only branch selector
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&branch=main";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleCommitSelector_isAllowed() throws Exception {
    // When: query with only commit selector
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&commit=abc123";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testSingleAsOfSelector_isAllowed() throws Exception {
    // When: query with only asOf selector
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D"
        + "&asOf=2024-01-01T00:00:00Z";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void testNoSelectors_isAllowed() throws Exception {
    // When: query with no selectors
    String url = "/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D";
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

    // Then: should not return 400
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.BAD_REQUEST);
  }
}
