package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.chucc.vcserver.testutil.KafkaTestContainers;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Integration test for Graph Store Protocol OPTIONS endpoint.
 * Tests capability discovery per SPARQL 1.2 Graph Store Protocol spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class GraphStoreProtocolDiscoveryIT {

  // Eager initialization - container must be started before @DynamicPropertySource
  private static KafkaContainer kafkaContainer = KafkaTestContainers.createKafkaContainer();

  @Autowired
  private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void configureKafka(DynamicPropertyRegistry registry) {
    registry.add("kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Unique consumer group per test class to prevent cross-test event consumption
    registry.add("spring.kafka.consumer.group-id",
        () -> "test-" + System.currentTimeMillis() + "-" + Math.random());
  }

  @Test
  void optionsEndpoint_shouldAdvertiseAllCapabilities() {
    // When
    ResponseEntity<Void> response = restTemplate.exchange(
        "/data",
        HttpMethod.OPTIONS,
        HttpEntity.EMPTY,
        Void.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

    HttpHeaders headers = response.getHeaders();

    // Check Allow header includes all GSP methods
    String allow = headers.getFirst(HttpHeaders.ALLOW);
    assertThat(allow).contains("GET");
    assertThat(allow).contains("PUT");
    assertThat(allow).contains("POST");
    assertThat(allow).contains("DELETE");
    assertThat(allow).contains("HEAD");
    assertThat(allow).contains("PATCH");
    assertThat(allow).contains("OPTIONS");

    // Check Accept-Patch advertises RDF Patch support
    assertThat(headers.getFirst(HttpHeaders.ACCEPT_PATCH)).isEqualTo("text/rdf-patch");

    // Check version control header
    assertThat(headers.getFirst("SPARQL-Version-Control")).isEqualTo("1.0");

    // Check Link header points to version control endpoint
    assertThat(headers.getFirst(HttpHeaders.LINK))
        .isEqualTo("</version>; rel=\"version-control\"");
  }

  @Test
  void optionsEndpoint_shouldReturnNoBody() {
    // When
    ResponseEntity<String> response = restTemplate.exchange(
        "/data",
        HttpMethod.OPTIONS,
        HttpEntity.EMPTY,
        String.class
    );

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).isNullOrEmpty();
  }
}
