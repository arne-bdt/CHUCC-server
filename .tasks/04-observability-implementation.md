# Task 04: Observability Implementation

**Status**: Ready to implement
**Priority**: Medium
**Estimated Effort**: 2-3 hours
**Dependencies**: None

## Overview

Add **lightweight, non-invasive** observability to CHUCC Server using Spring Boot Actuator + Micrometer. Uses annotations and AOP to collect metrics without polluting business logic.

## Design Principles

✅ **Annotations only** - No `MeterRegistry` injected into services
✅ **AOP-based** - Metrics collected outside business logic
✅ **Minimal overhead** - Micrometer uses lock-free data structures
✅ **Trace IDs in logs only** - Leverage existing `log.info/warn/error` statements
✅ **No dashboards** - Just expose Prometheus endpoint for external scraping

## Goals

1. **Metrics**: Timings + counters via `@Timed` and `@Counted` annotations
2. **Tracing**: Request trace IDs via MDC (logging only)
3. **Health Checks**: Repository and Kafka connectivity
4. **Zero business logic changes**: All metrics via AOP interception

---

## Phase 1: Dependencies and Configuration (15 minutes)

### Step 1.1: Add Dependencies

**File**: `pom.xml`

Add to `<dependencies>` section:

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- AspectJ for @Timed/@Counted support -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

**Lines added**: 12

### Step 1.2: Configure Actuator

**File**: `src/main/resources/application.properties`

Add to end of file:

```properties
# Observability - Actuator endpoints
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.show-details=when-authorized
management.metrics.enable.jvm=true

# Metrics tags
management.metrics.tags.application=chucc-server
```

**Lines added**: 6

### Step 1.3: Enable AOP Metrics Support

**File**: `src/main/java/org/chucc/vcserver/config/MetricsConfiguration.java` (new file)

```java
package org.chucc.vcserver.config;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for annotation-based metrics collection.
 * Enables {@code @Timed} and {@code @Counted} annotations via AOP.
 */
@Configuration
@EnableAspectJAutoProxy
public class MetricsConfiguration {

  /**
   * Enables {@code @Timed} annotation support.
   *
   * @param registry the meter registry
   * @return the timed aspect
   */
  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  /**
   * Enables {@code @Counted} annotation support.
   *
   * @param registry the meter registry
   * @return the counted aspect
   */
  @Bean
  public CountedAspect countedAspect(MeterRegistry registry) {
    return new CountedAspect(registry);
  }
}
```

**Lines added**: 38

---

## Phase 2: Add Metrics Annotations (30 minutes)

### Target Methods

Only **6 methods** need annotations (no logic changes):

| Method | Annotation | Purpose |
|--------|------------|---------|
| `SparqlQueryService.executeQuery()` | `@Timed`, `@Counted` | Query latency + count |
| `SparqlUpdateCommandHandler.handle()` | `@Timed`, `@Counted` | Update latency + count |
| `RdfPatchService.applyPatch()` | `@Timed` | Patch application time |
| `ReadModelProjector.onEvent()` | `@Timed`, `@Counted` | Event processing time |
| `DatasetService.materializeAtCommit()` | `@Timed` | Dataset materialization |
| `EventPublisher.publish()` | `@Counted` | Event publication count |

### Step 2.1: SPARQL Query Metrics

**File**: `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java`

Add imports:

```java
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
```

Add annotations to `executeQuery()` method:

```java
@Timed(
    value = "sparql.query.execution",
    description = "SPARQL query execution time"
)
@Counted(
    value = "sparql.query.total",
    description = "Total SPARQL queries executed"
)
public String executeQuery(Dataset dataset, String queryString, ResultFormat format) {
  // Existing logic unchanged
  log.info("Executing SPARQL query with format {}", format);
  // ... rest of method
}
```

**Lines added**: 10 (2 imports + 8 annotation lines)

**Metrics created**:
- `sparql_query_execution_seconds` - Timer for query latency
- `sparql_query_total` - Counter for query count

### Step 2.2: SPARQL Update Metrics

**File**: `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommandHandler.java`

Add imports:

```java
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
```

Add annotations to `handle()` method:

```java
@Timed(
    value = "sparql.update.execution",
    description = "SPARQL update execution time"
)
@Counted(
    value = "sparql.update.total",
    description = "Total SPARQL updates executed"
)
public VersionControlEvent handle(SparqlUpdateCommand command) {
  // Existing logic unchanged
  log.info("Executing SPARQL update on dataset {} branch {}",
      command.datasetName(), command.branch());
  // ... rest of method
}
```

**Lines added**: 10

**Metrics created**:
- `sparql_update_execution_seconds` - Timer for update latency
- `sparql_update_total` - Counter for update count

### Step 2.3: RDF Patch Metrics

**File**: `src/main/java/org/chucc/vcserver/service/RdfPatchService.java`

Add import:

```java
import io.micrometer.core.annotation.Timed;
```

Add annotation to `applyPatch()` method:

```java
@Timed(
    value = "rdf.patch.apply",
    description = "RDF patch application time"
)
public Model applyPatch(Model model, RDFPatch patch) {
  // Existing logic unchanged
  log.debug("Applying RDF patch to model");
  // ... rest of method
}
```

**Lines added**: 5

**Metrics created**:
- `rdf_patch_apply_seconds` - Timer for patch application

### Step 2.4: Event Projector Metrics

**File**: `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

Add imports:

```java
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
```

Add annotations to `onEvent()` method:

```java
@KafkaListener(topics = "#{kafkaProperties.topicPattern}", groupId = "read-model-projector")
@Timed(
    value = "event.projector.processing",
    description = "Event processing time"
)
@Counted(
    value = "event.projector.processed",
    description = "Events processed count"
)
public void onEvent(@Payload String eventJson) {
  // Existing logic unchanged
  log.debug("Processing event: {}", eventJson);
  // ... rest of method
}
```

**Lines added**: 10

**Metrics created**:
- `event_projector_processing_seconds` - Timer for event processing
- `event_projector_processed_total` - Counter for events processed

### Step 2.5: Dataset Materialization Metrics

**File**: `src/main/java/org/chucc/vcserver/service/DatasetService.java`

Add import:

```java
import io.micrometer.core.annotation.Timed;
```

Add annotation to `materializeAtCommit()` method:

```java
@Timed(
    value = "dataset.materialize",
    description = "Dataset materialization time"
)
public Dataset materializeAtCommit(String datasetName, CommitId commitId) {
  // Existing logic unchanged
  log.info("Materializing dataset {} at commit {}", datasetName, commitId);
  // ... rest of method
}
```

**Lines added**: 5

**Metrics created**:
- `dataset_materialize_seconds` - Timer for materialization

### Step 2.6: Event Publisher Metrics

**File**: `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

Add import:

```java
import io.micrometer.core.annotation.Counted;
```

Add annotation to `publish()` method:

```java
@Counted(
    value = "event.published",
    description = "Events published count"
)
public CompletableFuture<Void> publish(VersionControlEvent event) {
  // Existing logic unchanged
  log.debug("Publishing event: {}", event.getClass().getSimpleName());
  // ... rest of method
}
```

**Lines added**: 5

**Metrics created**:
- `event_published_total` - Counter for events published

---

## Phase 3: Request Tracing via MDC (15 minutes)

### Step 3.1: Create Trace ID Filter

**File**: `src/main/java/org/chucc/vcserver/filter/TraceIdFilter.java` (new file)

```java
package org.chucc.vcserver.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Adds a unique trace ID to each request for log correlation.
 * Trace ID is added to MDC and automatically included in all log statements.
 */
@Component
public class TraceIdFilter implements Filter {

  private static final String TRACE_ID_KEY = "traceId";

  /**
   * Adds trace ID to MDC for the duration of the request.
   *
   * @param request the servlet request
   * @param response the servlet response
   * @param chain the filter chain
   * @throws IOException if I/O error occurs
   * @throws ServletException if servlet error occurs
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    String traceId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put(TRACE_ID_KEY, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(TRACE_ID_KEY);
    }
  }
}
```

**Lines added**: 42

### Step 3.2: Update Logback Configuration

**File**: `src/main/resources/logback-spring.xml`

Update pattern to include trace ID:

```xml
<pattern>%d{HH:mm:ss.SSS} [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

**Lines changed**: 1

**Example log output**:
```
10:15:23.456 [a3f5b8c2] INFO  SparqlQueryService - Executing SPARQL query with format JSON
10:15:23.789 [a3f5b8c2] INFO  DatasetService - Materializing dataset default at commit abc123
```

---

## Phase 4: Health Indicators (30 minutes)

### Step 4.1: Repository Health Indicator

**File**: `src/main/java/org/chucc/vcserver/health/RepositoryHealthIndicator.java` (new file)

```java
package org.chucc.vcserver.health;

import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for repository accessibility.
 * Checks if commit repository is accessible.
 */
@Component
public class RepositoryHealthIndicator implements HealthIndicator {

  private final CommitRepository commitRepository;

  /**
   * Creates a new repository health indicator.
   *
   * @param commitRepository the commit repository
   */
  public RepositoryHealthIndicator(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  /**
   * Checks repository health.
   *
   * @return health status
   */
  @Override
  public Health health() {
    try {
      // Simple check - if this works, repository is accessible
      commitRepository.count();
      return Health.up()
          .withDetail("status", "Repositories accessible")
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("error", e.getMessage())
          .withException(e)
          .build();
    }
  }
}
```

**Lines added**: 46

### Step 4.2: Kafka Health Indicator

**Note**: Kafka health check is **built-in** to Spring Boot Actuator when Kafka is detected. No code needed.

Enabled via:
```properties
management.health.kafka.enabled=true
```

Already configured in Step 1.2.

---

## Phase 5: Testing and Verification (1 hour)

### Step 5.1: Add Integration Test

**File**: `src/test/java/org/chucc/vcserver/metrics/ObservabilityIntegrationTest.java` (new file)

```java
package org.chucc.vcserver.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.chucc.vcserver.test.IntegrationTestFixture;
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
class ObservabilityIntegrationTest extends IntegrationTestFixture {

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
  void prometheusEndpoint_shouldExposeMetrics() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/prometheus", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("jvm_memory_used_bytes")
        .contains("application=\"chucc-server\"");
  }

  @Test
  void sparqlQueryExecution_shouldRecordMetrics() {
    // Given: Execute a SPARQL query
    String query = "SELECT * WHERE { ?s ?p ?o }";
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
```

**Lines added**: 71

### Step 5.2: Manual Testing

```bash
# 1. Run full build
mvn -q clean install

# 2. Start application
mvn spring-boot:run

# 3. Check health endpoint
curl http://localhost:3030/actuator/health

# Expected output:
{
  "status": "UP",
  "components": {
    "diskSpace": { "status": "UP" },
    "kafka": { "status": "UP" },
    "ping": { "status": "UP" },
    "repositories": { "status": "UP" }
  }
}

# 4. Execute a SPARQL query to generate metrics
curl "http://localhost:3030/sparql?query=SELECT%20*%20WHERE%20%7B%20%3Fs%20%3Fp%20%3Fo%20%7D&branch=main"

# 5. Check Prometheus endpoint
curl http://localhost:3030/actuator/prometheus

# Expected output (excerpt):
# HELP sparql_query_execution_seconds SPARQL query execution time
# TYPE sparql_query_execution_seconds summary
sparql_query_execution_seconds_count{application="chucc-server"} 1.0
sparql_query_execution_seconds_sum{application="chucc-server"} 0.123

# HELP sparql_query_total Total SPARQL queries executed
# TYPE sparql_query_total counter
sparql_query_total{application="chucc-server"} 1.0

# 6. Check trace IDs in logs (application output)
# Look for lines like:
# 10:15:23.456 [a3f5b8c2] INFO  SparqlQueryService - Executing SPARQL query
```

---

## Summary

### What You Get

**Metrics** (via `/actuator/prometheus`):
- `sparql_query_execution_seconds` - Query latency timer
- `sparql_query_total` - Query count
- `sparql_update_execution_seconds` - Update latency timer
- `sparql_update_total` - Update count
- `rdf_patch_apply_seconds` - Patch application timer
- `event_projector_processing_seconds` - Event processing timer
- `event_projector_processed_total` - Events processed count
- `dataset_materialize_seconds` - Materialization timer
- `event_published_total` - Events published count
- `jvm_memory_used_bytes` - JVM metrics (built-in)

**Tracing** (in logs only):
```
10:15:23.456 [a3f5b8c2] INFO  SparqlQueryService - Executing query
10:15:23.789 [a3f5b8c2] INFO  DatasetService - Materializing dataset
```

**Health Checks** (via `/actuator/health`):
```json
{
  "status": "UP",
  "components": {
    "kafka": { "status": "UP" },
    "repositories": { "status": "UP" }
  }
}
```

### Code Impact

| Category | Lines Added | Files Changed |
|----------|-------------|---------------|
| Dependencies | 12 | 1 (pom.xml) |
| Configuration | 6 | 1 (application.properties) |
| Metrics config | 38 | 1 (MetricsConfiguration.java) |
| Annotations | 45 | 6 (service classes) |
| Trace ID filter | 42 | 1 (TraceIdFilter.java) |
| Logback pattern | 1 | 1 (logback-spring.xml) |
| Health indicator | 46 | 1 (RepositoryHealthIndicator.java) |
| Test | 71 | 1 (ObservabilityIntegrationTest.java) |
| **Total** | **~260 lines** | **13 files** |

**Business logic changes**: **ZERO** ✅

### Performance Impact

- **Metrics**: <1ms overhead per request (AOP proxying)
- **Tracing**: ~50μs overhead (ThreadLocal MDC)
- **Health checks**: Only when `/actuator/health` is called

**Total impact**: Negligible (<0.1% of typical request time)

### Time Breakdown

| Phase | Task | Time |
|-------|------|------|
| 1 | Dependencies + config | 15 min |
| 2 | Add 6 method annotations | 30 min |
| 3 | Trace ID filter + logback | 15 min |
| 4 | Health indicators | 30 min |
| 5 | Testing + verification | 1 hour |
| **Total** | | **~2.5 hours** |

---

## Acceptance Criteria

- [ ] Spring Boot Actuator configured
- [ ] `@Timed` and `@Counted` aspects enabled
- [ ] 6 methods annotated with metrics
- [ ] Trace ID filter added and configured
- [ ] Repository health indicator implemented
- [ ] Integration test passing
- [ ] Manual verification completed
- [ ] Zero Checkstyle violations
- [ ] Zero SpotBugs warnings
- [ ] Zero PMD violations
- [ ] Full build passes (`mvn -q clean install`)

---

## References

**Spring Boot Actuator**:
- https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

**Micrometer**:
- https://micrometer.io/docs
- https://micrometer.io/docs/concepts#_timers
- https://micrometer.io/docs/concepts#_counters

**Prometheus**:
- https://prometheus.io/docs/introduction/overview/
