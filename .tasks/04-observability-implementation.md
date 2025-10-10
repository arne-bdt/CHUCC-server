# Task 04: Observability Implementation

**Status**: Not started
**Priority**: Medium
**Estimated Effort**: 1-2 days
**Dependencies**: None

## Overview

Add comprehensive observability to CHUCC Server using Spring Boot Actuator + Micrometer. This enables production monitoring, performance analysis, and operational insights.

## Goals

1. **Metrics**: Track RDF patch application, query performance, event lag, commit rates
2. **Health Checks**: Kafka connectivity, repository health, system status
3. **Tracing**: Distributed tracing for request flows (optional)
4. **Dashboards**: Grafana dashboards for visualization (optional)

## Phase 1: Core Metrics (4-6 hours)

### Step 1.1: Add Dependencies (15 minutes)

**File**: `pom.xml`

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus (for Grafana) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Optional: Micrometer Tracing with Zipkin -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
    <optional>true</optional>
</dependency>
```

### Step 1.2: Configure Actuator (30 minutes)

**File**: `src/main/resources/application.properties`

```properties
# Actuator Configuration
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=when-authorized
management.endpoint.prometheus.enabled=true
management.metrics.enable.jvm=true
management.metrics.enable.process=true
management.metrics.enable.system=true

# Custom metrics prefix
management.metrics.tags.application=chucc-server
management.metrics.tags.environment=${ENVIRONMENT:dev}

# Health indicators
management.health.kafka.enabled=true

# Info endpoint
info.app.name=CHUCC Server
info.app.description=Versioned SPARQL Server with CQRS + Event Sourcing
info.app.version=@project.version@
```

### Step 1.3: Add Custom Metrics (3-4 hours)

#### Metric 1: RDF Patch Application Time

**File**: `src/main/java/org/chucc/vcserver/service/RdfPatchService.java`

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class RdfPatchService {
  private final MeterRegistry meterRegistry;

  @Autowired
  public RdfPatchService(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public Model applyPatch(Model graph, RDFPatch patch) {
    return Timer.builder("rdf.patch.apply")
        .description("Time to apply RDF patch to graph")
        .tag("operation", "apply")
        .register(meterRegistry)
        .record(() -> {
          // Existing logic
          RDFPatchOps.applyChange(graph, patch);
          return graph;
        });
  }

  public RDFPatch parsePatch(String patchText) {
    meterRegistry.counter("rdf.patch.parse", "result", "success").increment();
    try {
      return RDFPatchOps.read(new ByteArrayInputStream(
          patchText.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      meterRegistry.counter("rdf.patch.parse", "result", "error").increment();
      throw e;
    }
  }
}
```

**Metrics Created**:
- `rdf.patch.apply` (timer) - Patch application duration
- `rdf.patch.parse` (counter) - Parse success/error counts

#### Metric 2: SPARQL Query Performance

**File**: `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java`

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class SparqlQueryService {
  private final MeterRegistry meterRegistry;

  public String executeQuery(Dataset dataset, String queryString, ResultFormat format) {
    // Determine query type for tagging
    String queryType = determineQueryType(queryString);

    return Timer.builder("sparql.query.execution")
        .description("SPARQL query execution time")
        .tag("type", queryType) // SELECT, ASK, CONSTRUCT, DESCRIBE
        .tag("format", format.name()) // JSON, XML, CSV, etc.
        .register(meterRegistry)
        .record(() -> {
          try (QueryExecution qExec = QueryExecutionFactory.create(queryString, dataset)) {
            meterRegistry.counter("sparql.query.total",
                "type", queryType,
                "result", "success").increment();
            return formatResults(qExec, format);
          } catch (Exception e) {
            meterRegistry.counter("sparql.query.total",
                "type", queryType,
                "result", "error").increment();
            throw e;
          }
        });
  }

  private String determineQueryType(String queryString) {
    String upper = queryString.toUpperCase(Locale.ROOT).trim();
    if (upper.startsWith("SELECT")) return "SELECT";
    if (upper.startsWith("ASK")) return "ASK";
    if (upper.startsWith("CONSTRUCT")) return "CONSTRUCT";
    if (upper.startsWith("DESCRIBE")) return "DESCRIBE";
    return "UNKNOWN";
  }
}
```

**Metrics Created**:
- `sparql.query.execution` (timer) - Query duration by type and format
- `sparql.query.total` (counter) - Query count by type and result

#### Metric 3: SPARQL Update Performance

**File**: `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommandHandler.java`

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class SparqlUpdateCommandHandler {
  private final MeterRegistry meterRegistry;

  public VersionControlEvent handle(SparqlUpdateCommand command) {
    return Timer.builder("sparql.update.execution")
        .description("SPARQL update execution time")
        .tag("dataset", command.datasetName())
        .tag("branch", command.branch())
        .register(meterRegistry)
        .record(() -> {
          VersionControlEvent event = executeUpdate(command);

          if (event == null) {
            // No-op
            meterRegistry.counter("sparql.update.noop",
                "dataset", command.datasetName()).increment();
          } else {
            // Commit created
            meterRegistry.counter("sparql.update.commit",
                "dataset", command.datasetName()).increment();
          }

          return event;
        });
  }
}
```

**Metrics Created**:
- `sparql.update.execution` (timer) - Update duration
- `sparql.update.noop` (counter) - No-op update count
- `sparql.update.commit` (counter) - Commit creation count

#### Metric 4: Event Projector Lag

**File**: `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java`

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.support.KafkaHeaders;

@KafkaListener(topics = "#{kafkaProperties.topicPattern}", groupId = "read-model-projector")
public void onEvent(
    @Payload String eventJson,
    @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {

  // Calculate lag (time between event creation and processing)
  long lag = System.currentTimeMillis() - timestamp;
  meterRegistry.gauge("event.projector.lag.ms", lag);

  Timer.builder("event.projector.processing")
      .description("Event processing time")
      .tag("event_type", extractEventType(eventJson))
      .register(meterRegistry)
      .record(() -> {
        // Existing event handling logic
        processEvent(eventJson);
      });

  meterRegistry.counter("event.projector.processed",
      "event_type", extractEventType(eventJson)).increment();
}
```

**Metrics Created**:
- `event.projector.lag.ms` (gauge) - Lag between event creation and processing
- `event.projector.processing` (timer) - Event processing duration
- `event.projector.processed` (counter) - Events processed by type

#### Metric 5: Commit Creation Rate

**File**: `src/main/java/org/chucc/vcserver/event/EventPublisher.java`

```java
import io.micrometer.core.instrument.MeterRegistry;

public class EventPublisher {
  private final MeterRegistry meterRegistry;

  public CompletableFuture<Void> publish(VersionControlEvent event) {
    meterRegistry.counter("commit.created",
        "dataset", extractDataset(event),
        "event_type", event.getClass().getSimpleName()).increment();

    return kafkaTemplate.send(topic, eventJson)
        .thenAccept(result -> {
          meterRegistry.counter("event.published",
              "dataset", extractDataset(event),
              "result", "success").increment();
        })
        .exceptionally(ex -> {
          meterRegistry.counter("event.published",
              "dataset", extractDataset(event),
              "result", "error").increment();
          throw new RuntimeException(ex);
        });
  }
}
```

**Metrics Created**:
- `commit.created` (counter) - Commits created by dataset and type
- `event.published` (counter) - Event publication success/error

#### Metric 6: Dataset Materialization Cache

**File**: `src/main/java/org/chucc/vcserver/service/DatasetService.java`

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

@Service
public class DatasetService {
  private final Cache<String, DatasetGraph> cache;
  private final MeterRegistry meterRegistry;

  @PostConstruct
  public void initMetrics() {
    // Bind cache metrics
    CaffeineCacheMetrics.monitor(meterRegistry, cache, "dataset.cache");
  }

  public Dataset materializeAtCommit(String datasetName, CommitId commitId) {
    String cacheKey = datasetName + ":" + commitId.value();

    return Timer.builder("dataset.materialize")
        .description("Dataset materialization time")
        .tag("dataset", datasetName)
        .register(meterRegistry)
        .record(() -> {
          DatasetGraph graph = cache.get(cacheKey, key -> {
            meterRegistry.counter("dataset.cache.miss",
                "dataset", datasetName).increment();
            return buildDatasetGraph(datasetName, commitId);
          });

          if (graph != cache.getIfPresent(cacheKey)) {
            meterRegistry.counter("dataset.cache.hit",
                "dataset", datasetName).increment();
          }

          return DatasetFactory.wrap(graph);
        });
  }
}
```

**Metrics Created**:
- `dataset.cache.size` (gauge) - Cache size
- `dataset.cache.evictions` (counter) - Cache evictions
- `dataset.cache.hit` (counter) - Cache hits
- `dataset.cache.miss` (counter) - Cache misses
- `dataset.materialize` (timer) - Materialization duration

### Step 1.4: Add Custom Health Indicators (1 hour)

**File**: `src/main/java/org/chucc/vcserver/health/RepositoryHealthIndicator.java`

```java
package org.chucc.vcserver.health;

import org.chucc.vcserver.repository.BranchRepository;
import org.chucc.vcserver.repository.CommitRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class RepositoryHealthIndicator implements HealthIndicator {

  private final BranchRepository branchRepository;
  private final CommitRepository commitRepository;

  @Override
  public Health health() {
    try {
      // Check if repositories are accessible
      long branchCount = branchRepository.countByDataset("default");
      long commitCount = commitRepository.countByDataset("default");

      return Health.up()
          .withDetail("branches", branchCount)
          .withDetail("commits", commitCount)
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

**File**: `src/main/java/org/chucc/vcserver/health/EventPublisherHealthIndicator.java`

```java
@Component
public class EventPublisherHealthIndicator implements HealthIndicator {

  private final KafkaTemplate<String, String> kafkaTemplate;

  @Override
  public Health health() {
    try {
      // Check if Kafka is reachable (non-blocking)
      Map<String, Object> metrics = kafkaTemplate.metrics();

      return Health.up()
          .withDetail("kafka", "Connected")
          .withDetail("metrics_available", metrics.size())
          .build();
    } catch (Exception e) {
      return Health.down()
          .withDetail("kafka", "Disconnected")
          .withException(e)
          .build();
    }
  }
}
```

## Phase 2: Metrics Endpoint and Testing (2-3 hours)

### Step 2.1: Test Metrics Locally (30 minutes)

```bash
# Start application
mvn spring-boot:run

# Check health endpoint
curl http://localhost:3030/actuator/health
# Expected: {"status":"UP","components":{...}}

# Check metrics endpoint
curl http://localhost:3030/actuator/metrics
# Expected: {"names":["rdf.patch.apply","sparql.query.execution",...]}

# Check specific metric
curl http://localhost:3030/actuator/metrics/sparql.query.execution
# Expected: {"name":"sparql.query.execution","measurements":[...],"availableTags":[...]}

# Check Prometheus endpoint
curl http://localhost:3030/actuator/prometheus
# Expected: Prometheus-formatted metrics
```

### Step 2.2: Add Metrics Integration Test (1-2 hours)

**File**: `src/test/java/org/chucc/vcserver/metrics/MetricsIntegrationTest.java`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class MetricsIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private MeterRegistry meterRegistry;

  @Test
  void actuatorHealth_shouldReturnUp() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void metricsEndpoint_shouldExposeCustomMetrics() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "/actuator/metrics", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains(
        "rdf.patch.apply",
        "sparql.query.execution",
        "event.projector.lag.ms"
    );
  }

  @Test
  void sparqlQueryExecution_shouldRecordMetrics() {
    // Given: Execute a query
    String query = "SELECT * WHERE { ?s ?p ?o }";
    restTemplate.getForEntity(
        "/sparql?query=" + query + "&branch=main", String.class);

    // Then: Metric should be recorded
    Timer timer = meterRegistry.find("sparql.query.execution").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isGreaterThan(0);
  }
}
```

### Step 2.3: Document Metrics (1 hour)

**File**: `docs/operations/metrics.md`

```markdown
# CHUCC Server Metrics

## Available Metrics

### RDF Operations
- `rdf.patch.apply` - Time to apply RDF patches
- `rdf.patch.parse` - RDF patch parsing success/error counts

### SPARQL Queries
- `sparql.query.execution` - Query execution time (tagged by type, format)
- `sparql.query.total` - Total queries executed (tagged by type, result)

### SPARQL Updates
- `sparql.update.execution` - Update execution time
- `sparql.update.noop` - No-op updates count
- `sparql.update.commit` - Commits created count

### Event Processing
- `event.projector.lag.ms` - Event processing lag
- `event.projector.processing` - Event processing time
- `event.projector.processed` - Events processed count

### Dataset Cache
- `dataset.cache.size` - Cache size
- `dataset.cache.hit` - Cache hit count
- `dataset.cache.miss` - Cache miss count
- `dataset.materialize` - Dataset materialization time

### Commits
- `commit.created` - Commits created (tagged by dataset, type)
- `event.published` - Event publication success/error

## Accessing Metrics

### Prometheus Format
```
curl http://localhost:3030/actuator/prometheus
```

### JSON Format
```
curl http://localhost:3030/actuator/metrics
curl http://localhost:3030/actuator/metrics/sparql.query.execution
```
```

## Phase 3: Grafana Dashboard (Optional, 2-4 hours)

### Step 3.1: Create Prometheus Configuration

**File**: `docker/prometheus/prometheus.yml`

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'chucc-server'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:3030']
```

### Step 3.2: Create Grafana Dashboard JSON

**File**: `docker/grafana/dashboards/chucc-server.json`

Dashboard includes:
- SPARQL query rate and latency
- RDF patch application time
- Event projector lag
- Commit creation rate
- Cache hit ratio
- JVM metrics (heap, threads, GC)

### Step 3.3: Create Docker Compose

**File**: `docker-compose.metrics.yml`

```yaml
version: '3.8'
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - ./docker/grafana/dashboards:/etc/grafana/provisioning/dashboards
```

## Testing Strategy

### Unit Tests
```bash
mvn test -Dtest=MetricsIntegrationTest
```

### Manual Testing
1. Start application: `mvn spring-boot:run`
2. Execute queries and updates
3. Check metrics: `curl http://localhost:3030/actuator/prometheus`
4. Verify counters and timers increase

### Load Testing (Optional)
```bash
# Install k6 or Apache JMeter
# Run load test script
# Monitor metrics in Grafana
```

## Acceptance Criteria

### Phase 1: Core Metrics
- [ ] Spring Boot Actuator configured
- [ ] 6 custom metric types implemented
- [ ] Health indicators for repositories and Kafka
- [ ] Metrics endpoint returns all custom metrics
- [ ] Unit tests for metrics recording
- [ ] Zero Checkstyle violations

### Phase 2: Testing
- [ ] MetricsIntegrationTest passing
- [ ] Documentation created
- [ ] Manual testing verified

### Phase 3: Dashboards (Optional)
- [ ] Prometheus scraping configured
- [ ] Grafana dashboard created
- [ ] Docker Compose for observability stack

## Estimated Effort

| Phase | Task | Time |
|-------|------|------|
| 1 | Add dependencies and config | 45 min |
| 1 | Add 6 custom metrics | 3-4 hours |
| 1 | Add health indicators | 1 hour |
| 2 | Test and document | 2-3 hours |
| 3 | Grafana dashboard (optional) | 2-4 hours |
| **Total** | | **7-13 hours (1-2 days)** |

## References

**Spring Boot Actuator**:
- https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

**Micrometer**:
- https://micrometer.io/docs

**Grafana Dashboards**:
- https://grafana.com/grafana/dashboards/

**Example Metrics**:
- `src/main/java/org/chucc/vcserver/service/SparqlQueryService.java`
- `src/main/java/org/chucc/vcserver/command/SparqlUpdateCommandHandler.java`
