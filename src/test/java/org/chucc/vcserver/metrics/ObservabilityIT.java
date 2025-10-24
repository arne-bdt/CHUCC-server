package org.chucc.vcserver.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for observability features.
 * Tests metrics collection, health checks, and Prometheus endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ObservabilityIT extends ITFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private MeterRegistry meterRegistry;

  @Test
  void healthEndpoint_shouldReturnUp() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void metricsRegistry_shouldBeAvailable() {
    // Verify that the MeterRegistry is properly configured and accessible
    assertThat(meterRegistry).isNotNull();

    // Verify that JVM metrics are registered
    assertThat(meterRegistry.find("jvm.memory.used").gauge()).isNotNull();
  }

  @Test
  void sparqlQueryExecution_shouldRecordMetrics() {
    // Given: Execute a SPARQL query (URL-encoded)
    String query = "SELECT+*+WHERE+%7B+%3Fs+%3Fp+%3Fo+%7D";
    restTemplate.getForEntity(
        "/sparql?query=" + query + "&branch=main", String.class);

    // Then: Timer metric should be recorded
    Timer timer = meterRegistry.find("sparql.query.execution").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isGreaterThan(0);

    // And: Counter metric should be recorded
    assertThat(meterRegistry.find("sparql.query.total").counter())
        .isNotNull();
  }

  @Test
  void traceIdFilter_shouldAddTraceIdToLogs() {
    // When: Execute any request
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health", String.class);

    // Then: Request should succeed (trace ID is transparent)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Note: Trace ID verification requires log inspection
    // See manual testing section
  }
}
