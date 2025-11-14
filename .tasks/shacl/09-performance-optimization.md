# Task 9: Performance Optimization

**Phase:** 5 (Advanced Features - Optional)
**Estimated Time:** 3-4 hours
**Status:** Not Started
**Dependencies:** Tasks 1-8 (all previous tasks)

---

## Overview

This task implements performance optimizations for SHACL validation to improve throughput and reduce latency. These optimizations are optional but recommended for production deployments with high validation volumes.

**Key Features:**
- Shapes graph caching (avoid re-parsing identical shapes)
- Parallel validation (batch processing with CompletableFuture)
- Metrics and monitoring (Micrometer integration)
- Performance benchmarks

---

## Acceptance Criteria

- [ ] Shapes graph caching implemented with configurable TTL
- [ ] Parallel validation supports batch operations
- [ ] Micrometer metrics integrated (counters, timers, gauges)
- [ ] Performance benchmarks demonstrate improvements
- [ ] Cache eviction strategies implemented
- [ ] All integration tests pass (6 tests)
- [ ] All unit tests pass (8 tests)
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] Full build succeeds: `mvn -q clean install`

---

## 1. Shapes Graph Caching

### 1.1 Cache Configuration

**File:** `src/main/java/org/chucc/vcserver/config/ShaclCacheConfig.java`

```java
package org.chucc.vcserver.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.apache.jena.graph.Graph;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for SHACL validation caching.
 *
 * <p>Provides caching for parsed shapes graphs to avoid re-parsing
 * identical shapes on subsequent validations.
 */
@Configuration
public class ShaclCacheConfig {

  /**
   * Creates a Caffeine cache for shapes graphs.
   *
   * @param properties cache configuration properties
   * @return configured shapes cache
   */
  @Bean
  public Cache<String, Graph> shapesGraphCache(ShaclCacheProperties properties) {
    return Caffeine.newBuilder()
        .maximumSize(properties.maxSize())
        .expireAfterWrite(Duration.ofMinutes(properties.ttlMinutes()))
        .recordStats()
        .build();
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/config/ShaclCacheProperties.java`

```java
package org.chucc.vcserver.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for SHACL validation caching.
 */
@ConfigurationProperties(prefix = "chucc.shacl.cache")
@Validated
public record ShaclCacheProperties(

    /**
     * Maximum number of cached shapes graphs.
     */
    @Min(1) int maxSize,

    /**
     * Time-to-live for cached entries in minutes.
     */
    @Min(1) int ttlMinutes
) {

  /**
   * Default constructor with sensible defaults.
   */
  public ShaclCacheProperties {
    if (maxSize == 0) {
      maxSize = 100;  // Default: 100 cached shapes graphs
    }
    if (ttlMinutes == 0) {
      ttlMinutes = 60;  // Default: 1 hour TTL
    }
  }
}
```

### 1.2 Cache Integration in ShaclValidationService

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` (UPDATE)

Add cache support:

```java
@Service
public class ShaclValidationService {

  private final Cache<String, Graph> shapesGraphCache;

  public ShaclValidationService(
      ShaclValidationEngine validationEngine,
      GraphReferenceResolver graphResolver,
      Cache<String, Graph> shapesGraphCache,
      // ... other dependencies
  ) {
    this.shapesGraphCache = shapesGraphCache;
    // ...
  }

  /**
   * Resolves shapes graph with caching.
   *
   * @param shapesRef shapes graph reference
   * @return shapes graph
   */
  private Graph resolveShapesWithCache(GraphReference shapesRef) {
    // Generate cache key from shapes reference
    String cacheKey = generateCacheKey(shapesRef);

    // Try cache first
    Graph cachedShapes = shapesGraphCache.getIfPresent(cacheKey);
    if (cachedShapes != null) {
      log.debug("Cache hit for shapes: {}", cacheKey);
      return cachedShapes;
    }

    // Cache miss - resolve and cache
    log.debug("Cache miss for shapes: {}", cacheKey);
    Graph shapes = graphResolver.resolveGraph(shapesRef);
    shapesGraphCache.put(cacheKey, shapes);

    return shapes;
  }

  /**
   * Generates cache key from shapes reference.
   *
   * @param shapesRef shapes reference
   * @return cache key
   */
  private String generateCacheKey(GraphReference shapesRef) {
    // For inline shapes: hash of data
    if ("inline".equals(shapesRef.source())) {
      return "inline:" + hashData(shapesRef.data());
    }

    // For local shapes: dataset + graph + selector
    if ("local".equals(shapesRef.source())) {
      return String.format("local:%s:%s:%s:%s:%s",
          shapesRef.dataset(),
          shapesRef.graph(),
          shapesRef.branch(),
          shapesRef.commit(),
          shapesRef.asOf()
      );
    }

    // For remote shapes: endpoint + graph
    if ("remote".equals(shapesRef.source())) {
      return String.format("remote:%s:%s",
          shapesRef.endpoint(),
          shapesRef.graph()
      );
    }

    throw new IllegalArgumentException("Unknown shapes source: " + shapesRef.source());
  }

  /**
   * Computes SHA-256 hash of inline data.
   *
   * @param data inline RDF data
   * @return hex-encoded hash
   */
  private String hashData(String data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
```

### 1.3 Cache Eviction API

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclCacheController.java`

```java
package org.chucc.vcserver.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.apache.jena.graph.Graph;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for SHACL cache management.
 */
@RestController
@RequestMapping("/{dataset}/shacl/cache")
@Tag(name = "SHACL Cache Management", description = "Cache eviction and statistics")
public class ShaclCacheController {

  private final Cache<String, Graph> shapesGraphCache;

  public ShaclCacheController(Cache<String, Graph> shapesGraphCache) {
    this.shapesGraphCache = shapesGraphCache;
  }

  /**
   * Evicts all entries from shapes cache.
   *
   * @return 204 No Content
   */
  @DeleteMapping
  @Operation(summary = "Evict all cached shapes graphs")
  public ResponseEntity<Void> evictAll() {
    shapesGraphCache.invalidateAll();
    return ResponseEntity.noContent().build();
  }

  /**
   * Returns cache statistics.
   *
   * @return cache stats
   */
  @GetMapping("/stats")
  @Operation(summary = "Get cache statistics")
  public ResponseEntity<Map<String, Object>> getStats() {
    CacheStats stats = shapesGraphCache.stats();

    Map<String, Object> response = Map.of(
        "size", shapesGraphCache.estimatedSize(),
        "hitCount", stats.hitCount(),
        "missCount", stats.missCount(),
        "hitRate", stats.hitRate(),
        "evictionCount", stats.evictionCount(),
        "averageLoadPenalty", stats.averageLoadPenalty()
    );

    return ResponseEntity.ok(response);
  }
}
```

---

## 2. Parallel Validation

### 2.1 Batch Validation Support

**File:** `src/main/java/org/chucc/vcserver/dto/BatchValidationRequest.java`

```java
package org.chucc.vcserver.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request for batch validation of multiple data graphs.
 *
 * @param shapes shapes graph reference (shared across all validations)
 * @param dataGraphs list of data graph references to validate
 * @param options validation options (shared)
 */
public record BatchValidationRequest(

    @Valid GraphReference shapes,

    @NotEmpty
    @Size(max = 100)  // Limit batch size
    List<@Valid DataReference> dataGraphs,

    @Valid ValidationOptions options
) {

  /**
   * Compact constructor with defaults.
   */
  public BatchValidationRequest {
    if (options == null) {
      options = new ValidationOptions("separately");
    }
  }
}
```

**File:** `src/main/java/org/chucc/vcserver/dto/BatchValidationResponse.java`

```java
package org.chucc.vcserver.dto;

import java.time.Instant;
import java.util.List;

/**
 * Response for batch validation.
 *
 * @param results list of individual validation results
 * @param totalValidations total number of validations performed
 * @param successfulValidations number of conforming validations
 * @param failedValidations number of non-conforming validations
 * @param timestamp validation timestamp
 */
public record BatchValidationResponse(
    List<ValidationResult> results,
    int totalValidations,
    int successfulValidations,
    int failedValidations,
    Instant timestamp
) {

  /**
   * Individual validation result.
   */
  public record ValidationResult(
      DataReference data,
      boolean conforms,
      String reportUri
  ) {}
}
```

### 2.2 Parallel Validation Service

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` (UPDATE)

Add batch validation method:

```java
/**
 * Validates multiple data graphs in parallel.
 *
 * @param request batch validation request
 * @return batch validation response
 */
public BatchValidationResponse validateBatch(BatchValidationRequest request) {
  log.info("Starting batch validation of {} data graphs", request.dataGraphs().size());

  // Resolve shapes once (shared across all validations)
  Graph shapesGraph = resolveShapesWithCache(request.shapes());

  // Parallel validation using CompletableFuture
  List<CompletableFuture<BatchValidationResponse.ValidationResult>> futures =
      request.dataGraphs().stream()
          .map(dataRef -> CompletableFuture.supplyAsync(() ->
              validateSingle(shapesGraph, dataRef, request.options()),
              validationExecutor
          ))
          .toList();

  // Wait for all validations to complete
  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

  // Collect results
  List<BatchValidationResponse.ValidationResult> results = futures.stream()
      .map(CompletableFuture::join)
      .toList();

  // Compute statistics
  long successful = results.stream().filter(r -> r.conforms()).count();
  long failed = results.size() - successful;

  return new BatchValidationResponse(
      results,
      results.size(),
      (int) successful,
      (int) failed,
      Instant.now()
  );
}

/**
 * Validates a single data graph (helper for batch validation).
 */
private BatchValidationResponse.ValidationResult validateSingle(
    Graph shapesGraph,
    DataReference dataRef,
    ValidationOptions options
) {
  try {
    // Resolve data graph
    Graph dataGraph = graphResolver.resolveGraph(dataRef);

    // Validate
    ValidationReport report = validationEngine.validate(shapesGraph, dataGraph);

    return new BatchValidationResponse.ValidationResult(
        dataRef,
        report.conforms(),
        null  // No storage for batch validation
    );
  } catch (Exception e) {
    log.error("Validation failed for data graph: {}", dataRef, e);
    return new BatchValidationResponse.ValidationResult(
        dataRef,
        false,
        null
    );
  }
}
```

### 2.3 Executor Configuration

**File:** `src/main/java/org/chucc/vcserver/config/ShaclValidationConfig.java` (UPDATE)

Add executor bean:

```java
/**
 * Creates executor for parallel validation.
 *
 * @param properties validation properties
 * @return configured thread pool executor
 */
@Bean
public Executor validationExecutor(ShaclValidationProperties properties) {
  ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
  executor.setCorePoolSize(properties.maxConcurrent());
  executor.setMaxPoolSize(properties.maxConcurrent());
  executor.setQueueCapacity(100);
  executor.setThreadNamePrefix("shacl-validation-");
  executor.initialize();
  return executor;
}
```

### 2.4 Batch Validation Endpoint

**File:** `src/main/java/org/chucc/vcserver/controller/ShaclValidationController.java` (UPDATE)

Add batch endpoint:

```java
/**
 * Validates multiple data graphs in parallel.
 *
 * @param dataset dataset name
 * @param request batch validation request
 * @return batch validation response
 */
@PostMapping("/batch")
@Operation(summary = "Validate multiple data graphs in parallel")
public ResponseEntity<BatchValidationResponse> validateBatch(
    @PathVariable String dataset,
    @RequestBody @Valid BatchValidationRequest request
) {
  BatchValidationResponse response = validationService.validateBatch(request);
  return ResponseEntity.ok(response);
}
```

---

## 3. Metrics and Monitoring

### 3.1 Validation Metrics

**File:** `src/main/java/org/chucc/vcserver/service/ShaclValidationService.java` (UPDATE)

Add Micrometer metrics:

```java
@Service
public class ShaclValidationService {

  private final MeterRegistry meterRegistry;
  private final Counter validationCounter;
  private final Counter conformingCounter;
  private final Counter nonConformingCounter;
  private final Timer validationTimer;

  public ShaclValidationService(
      // ... existing dependencies
      MeterRegistry meterRegistry
  ) {
    // ... existing initialization

    // Metrics
    this.meterRegistry = meterRegistry;
    this.validationCounter = Counter.builder("shacl.validation.total")
        .description("Total number of SHACL validations")
        .register(meterRegistry);
    this.conformingCounter = Counter.builder("shacl.validation.conforming")
        .description("Number of conforming validations")
        .register(meterRegistry);
    this.nonConformingCounter = Counter.builder("shacl.validation.non_conforming")
        .description("Number of non-conforming validations")
        .register(meterRegistry);
    this.validationTimer = Timer.builder("shacl.validation.duration")
        .description("SHACL validation duration")
        .register(meterRegistry);
  }

  /**
   * Validates data graph against shapes graph (with metrics).
   */
  public ValidationResponse validate(ValidationRequest request) {
    return validationTimer.recordCallable(() -> {
      validationCounter.increment();

      ValidationResponse response = performValidation(request);

      if (response.conforms()) {
        conformingCounter.increment();
      } else {
        nonConformingCounter.increment();
      }

      return response;
    });
  }
}
```

### 3.2 Cache Metrics

**File:** `src/main/java/org/chucc/vcserver/config/ShaclCacheConfig.java` (UPDATE)

Add cache metrics registration:

```java
@Bean
public Cache<String, Graph> shapesGraphCache(
    ShaclCacheProperties properties,
    MeterRegistry meterRegistry
) {
  Cache<String, Graph> cache = Caffeine.newBuilder()
      .maximumSize(properties.maxSize())
      .expireAfterWrite(Duration.ofMinutes(properties.ttlMinutes()))
      .recordStats()
      .build();

  // Register cache metrics with Micrometer
  CaffeineCacheMetrics.monitor(meterRegistry, cache, "shapesGraphCache");

  return cache;
}
```

**File:** `pom.xml` (UPDATE)

Add Micrometer Caffeine integration:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
</dependency>
```

### 3.3 Metrics Endpoint

Metrics are automatically exposed via Spring Boot Actuator at `/actuator/metrics`.

**Key Metrics:**
- `shacl.validation.total` - Total validations
- `shacl.validation.conforming` - Conforming validations
- `shacl.validation.non_conforming` - Non-conforming validations
- `shacl.validation.duration` - Validation duration (histogram)
- `cache.size{name="shapesGraphCache"}` - Cache size
- `cache.gets{name="shapesGraphCache",result="hit"}` - Cache hits
- `cache.gets{name="shapesGraphCache",result="miss"}` - Cache misses
- `cache.evictions{name="shapesGraphCache"}` - Cache evictions

---

## 4. Configuration

### 4.1 Application Properties

**File:** `src/main/resources/application.yml` (UPDATE)

```yaml
chucc:
  shacl:
    validation:
      # ... existing configuration
      max-concurrent: 10  # Used for executor pool size

    cache:
      max-size: 100       # Maximum cached shapes graphs
      ttl-minutes: 60     # Cache entry TTL (1 hour)
```

---

## 5. Testing

### 5.1 Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/ShaclCacheIT.java`

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import org.apache.jena.graph.Graph;
import org.chucc.vcserver.dto.GraphReference;
import org.chucc.vcserver.dto.ValidationRequest;
import org.chucc.vcserver.dto.ValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for SHACL shapes caching.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ShaclCacheIT extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private Cache<String, Graph> shapesGraphCache;

  @Test
  void validate_withInlineShapes_shouldCacheShapes() {
    // Arrange
    String shapes = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        @prefix ex: <http://example.org/> .

        ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
            sh:path ex:name ;
            sh:minCount 1 ;
          ] .
        """;

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, shapes, null, null, null),
        null,
        null,
        null
    );

    // Act: First validation
    long sizeBefore = shapesGraphCache.estimatedSize();
    ResponseEntity<ValidationResponse> response1 = restTemplate.postForEntity(
        "/" + dataset + "/shacl",
        new HttpEntity<>(request),
        ValidationResponse.class
    );
    long sizeAfter = shapesGraphCache.estimatedSize();

    // Assert: Cache populated
    assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(sizeAfter).isGreaterThan(sizeBefore);

    // Act: Second validation (same shapes)
    long hitsBefore = shapesGraphCache.stats().hitCount();
    ResponseEntity<ValidationResponse> response2 = restTemplate.postForEntity(
        "/" + dataset + "/shacl",
        new HttpEntity<>(request),
        ValidationResponse.class
    );
    long hitsAfter = shapesGraphCache.stats().hitCount();

    // Assert: Cache hit
    assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(hitsAfter).isGreaterThan(hitsBefore);
  }

  @Test
  void cacheEviction_shouldClearAllEntries() {
    // Arrange: Populate cache
    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null, null, null),
        null,
        null,
        null
    );
    restTemplate.postForEntity("/" + dataset + "/shacl", new HttpEntity<>(request), ValidationResponse.class);

    assertThat(shapesGraphCache.estimatedSize()).isGreaterThan(0);

    // Act: Evict all
    ResponseEntity<Void> response = restTemplate.exchange(
        "/" + dataset + "/shacl/cache",
        HttpMethod.DELETE,
        null,
        Void.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(shapesGraphCache.estimatedSize()).isEqualTo(0);
  }

  @Test
  void cacheStats_shouldReturnStatistics() {
    // Act
    ResponseEntity<Map> response = restTemplate.getForEntity(
        "/" + dataset + "/shacl/cache/stats",
        Map.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsKeys("size", "hitCount", "missCount", "hitRate");
  }
}
```

**File:** `src/test/java/org/chucc/vcserver/integration/BatchValidationIT.java`

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.chucc.vcserver.dto.BatchValidationRequest;
import org.chucc.vcserver.dto.BatchValidationResponse;
import org.chucc.vcserver.dto.DataReference;
import org.chucc.vcserver.dto.GraphReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for batch validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class BatchValidationIT extends IntegrationTestFixture {

  @Test
  void validateBatch_withMultipleGraphs_shouldValidateInParallel() {
    // Arrange
    String shapes = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        @prefix ex: <http://example.org/> .

        ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
            sh:path ex:name ;
            sh:minCount 1 ;
          ] .
        """;

    List<DataReference> dataGraphs = List.of(
        new DataReference("inline", null, null, null, null,
            "@prefix ex: <http://example.org/> . ex:alice a ex:Person ; ex:name \"Alice\" .",
            null, null, null),
        new DataReference("inline", null, null, null, null,
            "@prefix ex: <http://example.org/> . ex:bob a ex:Person ; ex:name \"Bob\" .",
            null, null, null),
        new DataReference("inline", null, null, null, null,
            "@prefix ex: <http://example.org/> . ex:carol a ex:Person .",  // Missing name (invalid)
            null, null, null)
    );

    BatchValidationRequest request = new BatchValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, shapes, null, null, null),
        dataGraphs,
        null
    );

    // Act
    ResponseEntity<BatchValidationResponse> response = restTemplate.postForEntity(
        "/" + dataset + "/shacl/batch",
        new HttpEntity<>(request),
        BatchValidationResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalValidations()).isEqualTo(3);
    assertThat(response.getBody().successfulValidations()).isEqualTo(2);  // alice, bob
    assertThat(response.getBody().failedValidations()).isEqualTo(1);      // carol
    assertThat(response.getBody().results()).hasSize(3);
  }

  @Test
  void validateBatch_withTooManyGraphs_shouldRejectRequest() {
    // Arrange: 101 graphs (exceeds max 100)
    List<DataReference> dataGraphs = new ArrayList<>();
    for (int i = 0; i < 101; i++) {
      dataGraphs.add(new DataReference("inline", null, null, null, null,
          "@prefix ex: <http://example.org/> . ex:person" + i + " a ex:Person .",
          null, null, null));
    }

    BatchValidationRequest request = new BatchValidationRequest(
        new GraphReference("inline", "turtle", null, null, null, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null, null, null),
        dataGraphs,
        null
    );

    // Act
    ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(
        "/" + dataset + "/shacl/batch",
        new HttpEntity<>(request),
        ValidationResponse.class
    );

    // Assert: Validation error (batch too large)
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }
}
```

**File:** `src/test/java/org/chucc/vcserver/integration/ValidationMetricsIT.java`

```java
package org.chucc.vcserver.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.chucc.vcserver.dto.GraphReference;
import org.chucc.vcserver.dto.ValidationRequest;
import org.chucc.vcserver.dto.ValidationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for SHACL validation metrics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class ValidationMetricsIT extends IntegrationTestFixture {

  @Autowired
  private MeterRegistry meterRegistry;

  @Test
  void validate_shouldRecordMetrics() {
    // Arrange
    Counter totalCounter = meterRegistry.counter("shacl.validation.total");
    Counter conformingCounter = meterRegistry.counter("shacl.validation.conforming");

    double totalBefore = totalCounter.count();
    double conformingBefore = conformingCounter.count();

    ValidationRequest request = new ValidationRequest(
        new GraphReference("inline", "turtle", null, null, null,
            "@prefix sh: <http://www.w3.org/ns/shacl#> .",
            null, null, null),
        null,
        null,
        null
    );

    // Act
    ResponseEntity<ValidationResponse> response = restTemplate.postForEntity(
        "/" + dataset + "/shacl",
        new HttpEntity<>(request),
        ValidationResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(totalCounter.count()).isEqualTo(totalBefore + 1);

    if (response.getBody().conforms()) {
      assertThat(conformingCounter.count()).isEqualTo(conformingBefore + 1);
    }
  }
}
```

### 5.2 Unit Tests

**File:** `src/test/java/org/chucc/vcserver/service/ShapesGraphCacheTest.java`

```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.jena.graph.Graph;
import org.chucc.vcserver.dto.GraphReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for shapes graph caching.
 */
class ShapesGraphCacheTest {

  private Cache<String, Graph> cache;
  private GraphReferenceResolver resolver;
  private ShaclValidationService service;

  @BeforeEach
  void setUp() {
    cache = Caffeine.newBuilder().recordStats().build();
    resolver = mock(GraphReferenceResolver.class);
    // ... initialize service with cache
  }

  @Test
  void cacheKey_forInlineShapes_shouldUseDataHash() {
    // Arrange
    String data = "@prefix sh: <http://www.w3.org/ns/shacl#> .";
    GraphReference ref = new GraphReference("inline", "turtle", null, null, null, data, null, null, null);

    // Act
    String key1 = service.generateCacheKey(ref);
    String key2 = service.generateCacheKey(new GraphReference("inline", "turtle", null, null, null, data, null, null, null));

    // Assert: Same data = same key
    assertThat(key1).isEqualTo(key2);
    assertThat(key1).startsWith("inline:");
  }

  @Test
  void cacheKey_forLocalShapes_shouldIncludeSelectors() {
    // Arrange
    GraphReference ref1 = new GraphReference("local", "turtle", "dataset1", "shapes", null, null, "main", null, null);
    GraphReference ref2 = new GraphReference("local", "turtle", "dataset1", "shapes", null, null, "dev", null, null);

    // Act
    String key1 = service.generateCacheKey(ref1);
    String key2 = service.generateCacheKey(ref2);

    // Assert: Different branches = different keys
    assertThat(key1).isNotEqualTo(key2);
    assertThat(key1).contains("main");
    assertThat(key2).contains("dev");
  }

  @Test
  void resolveShapesWithCache_onCacheHit_shouldNotCallResolver() {
    // Arrange
    GraphReference ref = new GraphReference("inline", "turtle", null, null, null, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null, null, null);
    Graph mockGraph = mock(Graph.class);
    cache.put(service.generateCacheKey(ref), mockGraph);

    // Act
    Graph result = service.resolveShapesWithCache(ref);

    // Assert
    assertThat(result).isSameAs(mockGraph);
    verify(resolver, never()).resolveGraph(any());
    assertThat(cache.stats().hitCount()).isEqualTo(1);
  }

  @Test
  void resolveShapesWithCache_onCacheMiss_shouldCallResolverAndCache() {
    // Arrange
    GraphReference ref = new GraphReference("inline", "turtle", null, null, null, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null, null, null);
    Graph mockGraph = mock(Graph.class);
    when(resolver.resolveGraph(ref)).thenReturn(mockGraph);

    // Act
    Graph result = service.resolveShapesWithCache(ref);

    // Assert
    assertThat(result).isSameAs(mockGraph);
    verify(resolver, times(1)).resolveGraph(ref);
    assertThat(cache.stats().missCount()).isEqualTo(1);
    assertThat(cache.estimatedSize()).isEqualTo(1);
  }
}
```

**File:** `src/test/java/org/chucc/vcserver/service/BatchValidationTest.java`

```java
package org.chucc.vcserver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.Executor;
import org.apache.jena.graph.Graph;
import org.chucc.vcserver.dto.BatchValidationRequest;
import org.chucc.vcserver.dto.BatchValidationResponse;
import org.chucc.vcserver.dto.DataReference;
import org.chucc.vcserver.dto.GraphReference;
import org.chucc.vcserver.shacl.ValidationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit tests for batch validation.
 */
class BatchValidationTest {

  private ShaclValidationService service;
  private ShaclValidationEngine engine;
  private GraphReferenceResolver resolver;
  private Executor executor;

  @BeforeEach
  void setUp() {
    engine = mock(ShaclValidationEngine.class);
    resolver = mock(GraphReferenceResolver.class);

    // Use real executor for parallel testing
    ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
    taskExecutor.setCorePoolSize(4);
    taskExecutor.initialize();
    executor = taskExecutor;

    // ... initialize service
  }

  @Test
  void validateBatch_withMultipleGraphs_shouldValidateAllInParallel() {
    // Arrange
    GraphReference shapes = new GraphReference("inline", "turtle", null, null, null, "@prefix sh: <http://www.w3.org/ns/shacl#> .", null, null, null);
    List<DataReference> dataGraphs = List.of(
        new DataReference("inline", null, null, null, null, "data1", null, null, null),
        new DataReference("inline", null, null, null, null, "data2", null, null, null),
        new DataReference("inline", null, null, null, null, "data3", null, null, null)
    );

    Graph mockShapes = mock(Graph.class);
    Graph mockData1 = mock(Graph.class);
    Graph mockData2 = mock(Graph.class);
    Graph mockData3 = mock(Graph.class);

    when(resolver.resolveGraph(shapes)).thenReturn(mockShapes);
    when(resolver.resolveGraph(dataGraphs.get(0))).thenReturn(mockData1);
    when(resolver.resolveGraph(dataGraphs.get(1))).thenReturn(mockData2);
    when(resolver.resolveGraph(dataGraphs.get(2))).thenReturn(mockData3);

    ValidationReport report1 = mock(ValidationReport.class);
    ValidationReport report2 = mock(ValidationReport.class);
    ValidationReport report3 = mock(ValidationReport.class);
    when(report1.conforms()).thenReturn(true);
    when(report2.conforms()).thenReturn(true);
    when(report3.conforms()).thenReturn(false);

    when(engine.validate(mockShapes, mockData1)).thenReturn(report1);
    when(engine.validate(mockShapes, mockData2)).thenReturn(report2);
    when(engine.validate(mockShapes, mockData3)).thenReturn(report3);

    BatchValidationRequest request = new BatchValidationRequest(shapes, dataGraphs, null);

    // Act
    BatchValidationResponse response = service.validateBatch(request);

    // Assert
    assertThat(response.totalValidations()).isEqualTo(3);
    assertThat(response.successfulValidations()).isEqualTo(2);
    assertThat(response.failedValidations()).isEqualTo(1);
    assertThat(response.results()).hasSize(3);

    // Verify shapes resolved only once
    verify(resolver, times(1)).resolveGraph(shapes);
  }
}
```

---

## 6. Performance Benchmarks

### 6.1 Benchmark Utilities

**File:** `src/test/java/org/chucc/vcserver/benchmark/ValidationBenchmark.java`

```java
package org.chucc.vcserver.benchmark;

import java.util.ArrayList;
import java.util.List;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.chucc.vcserver.service.ShaclValidationEngine;
import org.chucc.vcserver.shacl.ValidationReport;
import org.openjdk.jmh.annotations.*;

/**
 * JMH benchmark for SHACL validation performance.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ValidationBenchmark {

  private ShaclValidationEngine engine;
  private Graph shapesGraph;
  private List<Graph> dataGraphs;

  @Setup
  public void setUp() {
    engine = new ShaclValidationEngine();

    // Load shapes graph
    shapesGraph = RDFDataMgr.loadGraph("src/test/resources/benchmark/shapes.ttl");

    // Load 100 data graphs
    dataGraphs = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      dataGraphs.add(RDFDataMgr.loadGraph("src/test/resources/benchmark/data" + i + ".ttl"));
    }
  }

  @Benchmark
  public void sequentialValidation() {
    for (Graph data : dataGraphs) {
      ValidationReport report = engine.validate(shapesGraph, data);
    }
  }

  @Benchmark
  public void parallelValidation() {
    dataGraphs.parallelStream()
        .forEach(data -> engine.validate(shapesGraph, data));
  }
}
```

**Expected Results:**
- Sequential validation: ~10ms per graph (1000ms total for 100 graphs)
- Parallel validation: ~200ms total for 100 graphs (5x speedup with 10 threads)

---

## 7. Documentation Updates

### 7.1 README.md Updates

**File:** `.tasks/shacl/README.md` (UPDATE)

Add performance optimization section:

```markdown
### Performance Features

- **Shapes Caching**: Parsed shapes graphs cached with configurable TTL (default: 1 hour)
- **Parallel Validation**: Batch validation endpoint validates multiple graphs concurrently
- **Metrics**: Micrometer integration tracks validation throughput, cache hits, duration
- **Cache Management**: REST API for cache eviction and statistics

**Endpoints:**
- `POST /{dataset}/shacl/batch` - Validate multiple graphs in parallel
- `DELETE /{dataset}/shacl/cache` - Evict all cached shapes
- `GET /{dataset}/shacl/cache/stats` - Get cache statistics
- `GET /actuator/metrics/shacl.validation.*` - Validation metrics
```

---

## 8. Success Criteria Checklist

Before marking this task complete, verify:

- [ ] Shapes graph caching implemented with Caffeine
- [ ] Cache configuration properties added (max-size, ttl-minutes)
- [ ] Cache eviction REST API implemented
- [ ] Batch validation endpoint created
- [ ] Parallel validation using CompletableFuture
- [ ] Micrometer metrics integrated (counters, timers, gauges)
- [ ] Cache metrics registered
- [ ] Integration tests pass (6 tests: cache, batch, metrics)
- [ ] Unit tests pass (8 tests: cache key generation, cache hit/miss, batch)
- [ ] Performance benchmarks demonstrate improvements
- [ ] Zero Checkstyle violations: `mvn -q checkstyle:check`
- [ ] Zero SpotBugs warnings: `mvn -q spotbugs:check`
- [ ] Zero PMD violations: `mvn -q pmd:check pmd:cpd-check`
- [ ] Zero compiler warnings
- [ ] Full build succeeds: `mvn -q clean install`
- [ ] Documentation updated (README.md)

---

## 9. Completion Steps

1. **Mark task complete in TASK_LIST.md:**
   ```markdown
   - ✅ Task 9: Performance Optimization - COMPLETED (YYYY-MM-DD)
   ```

2. **Update README.md progress:**
   ```markdown
   **Phase 5:** ✅ Completed (1/1 tasks)
   **Overall Progress:** 100% (9/9 tasks completed)
   ```

3. **Delete this task file:**
   ```bash
   rm .tasks/shacl/09-performance-optimization.md
   ```

4. **Create commit:**
   ```
   feat: add SHACL validation performance optimizations

   Implements caching, parallel validation, and metrics:
   - Caffeine cache for shapes graphs (configurable TTL)
   - Batch validation endpoint with CompletableFuture parallelization
   - Micrometer metrics (validation counters, timers, cache stats)
   - Cache management REST API (eviction, statistics)
   - Performance benchmarks (5x speedup for batch validation)

   Integration tests: 6 (cache, batch, metrics)
   Unit tests: 8 (cache logic, parallel validation)

   🤖 Generated with [Claude Code](https://claude.com/claude-code)

   Co-Authored-By: Claude <noreply@anthropic.com>
   ```

---

**Task Created:** 2025-11-14
**Status:** Ready for implementation
**Dependencies:** Tasks 1-8 must be completed first
**Estimated Time:** 3-4 hours
