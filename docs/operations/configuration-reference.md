# Configuration Reference

Complete reference for all CHUCC Server configuration properties.

---

## Table of Contents

- [Projection Retry Configuration](#projection-retry-configuration)
- [Kafka Configuration](#kafka-configuration)
- [Materialized Views Configuration](#materialized-views-configuration)
- [Projector Configuration](#projector-configuration)
- [Server Configuration](#server-configuration)

---

## Projection Retry Configuration

**Property Prefix:** `chucc.projection.retry`

**Added:** 2025-10-28

Controls retry behavior for failed event projections.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `max-attempts` | int | 10 | Maximum retry attempts before DLQ |
| `initial-interval` | long | 1000 | Initial backoff interval (ms) |
| `multiplier` | double | 2.0 | Exponential backoff multiplier |
| `max-interval` | long | 60000 | Maximum backoff interval cap (ms) |

### Example Configuration

```yaml
chucc:
  projection:
    retry:
      max-attempts: 10           # Total retry attempts (default: 10)
      initial-interval: 1000     # Initial backoff in ms (default: 1s)
      multiplier: 2.0            # Exponential multiplier (default: 2.0)
      max-interval: 60000        # Max backoff in ms (default: 60s)
```

### Backoff Calculation

```
interval(attempt) = min(initial-interval × (multiplier ^ (attempt - 1)), max-interval)
```

**Example progression (defaults):**
- Attempt 1: 1000ms (1s)
- Attempt 2: 2000ms (2s)
- Attempt 3: 4000ms (4s)
- Attempt 4: 8000ms (8s)
- Attempt 5: 16000ms (16s)
- Attempt 6: 32000ms (32s)
- Attempt 7: 60000ms (60s, capped)
- Attempt 8-10: 60000ms (capped)

### Tuning Guidance

**Increase max-attempts if:**
- Transient failures common (network instability, temporary resource exhaustion)
- Acceptable to delay projection for longer period

**Decrease max-attempts if:**
- Fast failure detection desired
- DLQ investigation preferred over long retry

**Increase initial-interval if:**
- Downstream systems need longer recovery time
- Reducing retry load on external dependencies

**Decrease multiplier if:**
- Linear backoff preferred over exponential
- Smoother retry distribution desired

---

## Kafka Configuration

**Property Prefix:** `chucc.kafka`

Configuration for Kafka integration and topic management.

### Core Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `bootstrap-servers` | String | localhost:9092 | Kafka broker addresses |
| `topic-template` | String | vc.{dataset}.events | Topic naming pattern |
| `partitions` | int | 3 | Number of partitions per topic |
| `replication-factor` | int | 1 (dev), 3 (prod) | Replication factor |
| `retention-ms` | long | -1 | Retention period (-1 = infinite) |
| `compaction` | boolean | false | Enable log compaction |

### Example Configuration

```yaml
chucc:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    topic-template: vc.{dataset}.events
    partitions: 6
    replication-factor: 3
    retention-ms: -1  # Infinite retention for event sourcing
    compaction: false # Append-only
```

### DLQ Configuration

DLQ topics are created automatically with these settings:

| Setting | Value | Notes |
|---------|-------|-------|
| **Topic Name** | `{source-topic}.dlq` | Example: `vc.default.events.dlq` |
| **Partitions** | Same as source topic | Maintains parallelism |
| **Replication Factor** | `min(source-rf, 2)` | Max RF=2 for DLQ |
| **Retention** | 7 days (604800000 ms) | Configurable via constant |
| **Cleanup Policy** | delete | Append-only |

---

## Materialized Views Configuration

**Property Prefix:** `chucc.materialized-views`

**Added:** 2025-10-27

Configuration for materialized branch graph cache.

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `max-branches` | int | 25 | Maximum cached branches (LRU eviction) |
| `monitoring.enabled` | boolean | true | Enable periodic statistics logging |
| `monitoring.interval-seconds` | int | 300 | Statistics logging interval (5 minutes) |

### Example Configuration

```yaml
chucc:
  materialized-views:
    max-branches: 50              # Increase cache size
    monitoring:
      enabled: true               # Enable monitoring
      interval-seconds: 600       # Log stats every 10 minutes
```

### Tuning Guidance

**Increase max-branches if:**
- Large number of active branches (>25)
- Sufficient heap memory available
- High cache miss rate observed

**Decrease max-branches if:**
- OutOfMemoryError occurs
- Limited heap memory
- Few branches actively queried

**Cache Size Estimation:**
```
Memory per branch ≈ 10-50 MB (depends on graph size)
Total memory ≈ max-branches × avg-graph-size
```

**Example:**
- 50 branches × 20 MB = 1 GB cache
- Requires ~2-3 GB heap total

---

## Projector Configuration

**Property Prefix:** `projector`

Configuration for event deduplication and projector behavior.

### Deduplication Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `deduplication.enabled` | boolean | true | Enable event deduplication |
| `deduplication.cache-size` | int | 10000 | LRU cache size for event IDs |

### Kafka Listener Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `kafka-listener.enabled` | boolean | true | Enable projector (false in tests) |

### Example Configuration

```yaml
projector:
  deduplication:
    enabled: true
    cache-size: 10000
  kafka-listener:
    enabled: true  # Disabled in tests
```

### Tuning Guidance

**Increase cache-size if:**
- High retry rate (many duplicate events)
- Long retry windows (backoff > 60s)
- Multiple event types with same ID

**Disable deduplication if:**
- Events guaranteed unique (no retries)
- Performance critical (cache lookup overhead)
- Idempotent projector logic (safe for duplicates)

---

## Server Configuration

**Property Prefix:** `server`

Standard Spring Boot server configuration.

### Common Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `port` | int | 3030 | HTTP server port |
| `servlet.context-path` | String | / | Application context path |

### Example Configuration

```yaml
server:
  port: 8080
  servlet:
    context-path: /chucc
```

---

## Spring Kafka Configuration

**Property Prefix:** `spring.kafka`

Standard Spring Kafka configuration.

### Critical Settings

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

    consumer:
      # CRITICAL: Disable auto-commit for fail-fast behavior
      enable-auto-commit: false

      # Consumer group for projector
      group-id: chucc-server-projector

    listener:
      # CRITICAL: Per-record ACK for retry support
      ack-mode: record
```

**Why these settings matter:**
- `enable-auto-commit: false` - Prevents silent data loss on projection failures
- `ack-mode: record` - Only commits offset after successful projection
- Together: Enable automatic retry and DLQ routing

See [CQRS Guide](../architecture/cqrs-event-sourcing.md#exception-handling) for detailed explanation.

---

## Environment Variables

Common environment variables for production deployment:

| Variable | Example | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka-1:9092,kafka-2:9092` | Kafka broker addresses |
| `JAVA_OPTS` | `-Xms2g -Xmx4g` | JVM heap settings |
| `SERVER_PORT` | `8080` | HTTP server port |
| `SPRING_PROFILES_ACTIVE` | `prod` | Active Spring profile |

### Example Docker Compose

```yaml
services:
  chucc-server:
    image: chucc-server:latest
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JAVA_OPTS: -Xms2g -Xmx4g
      SERVER_PORT: 8080
    ports:
      - "8080:8080"
```

---

## Configuration Profiles

### Development Profile

**File:** `src/main/resources/application-dev.yml`

```yaml
chucc:
  kafka:
    bootstrap-servers: localhost:9092
    replication-factor: 1  # Single broker
  materialized-views:
    max-branches: 10       # Small cache
```

### Production Profile

**File:** `src/main/resources/application-prod.yml`

```yaml
chucc:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    replication-factor: 3  # High availability
  materialized-views:
    max-branches: 100      # Large cache
```

### Test Profile

**File:** `src/test/resources/test.properties`

```yaml
projector:
  kafka-listener:
    enabled: false  # Disabled by default (ITFixture pattern)
```

---

## Monitoring Configuration

### Actuator Endpoints

Enable all actuator endpoints in production:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Available Health Checks

- `GET /actuator/health` - Overall system health
- `GET /actuator/health/materializedViews` - Materialized views health
- `GET /actuator/kafka/health-detailed?dataset={name}` - Kafka topic health

### Available Metrics

- `chucc.projection.events.total` - Event processing outcomes
- `chucc.projection.retries.total` - Retry attempts per topic
- `materialized_branch_cache.*` - Cache statistics
- `dataset.creation.time` - Dataset creation duration

See [Runbook](./runbook.md) for monitoring procedures.

---

## Validation

Configuration properties are validated at startup:

**ProjectionRetryProperties validation:**
- `max-attempts` must be > 0
- `initial-interval` must be > 0
- `multiplier` must be >= 1.0
- `max-interval` must be > 0

**MaterializedViewsProperties validation:**
- `max-branches` must be >= 1

Invalid configuration causes application startup failure with clear error message.

---

## Complete Example

Full production configuration:

```yaml
server:
  port: 8080

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    consumer:
      enable-auto-commit: false
      group-id: chucc-server-projector
    listener:
      ack-mode: record

chucc:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    topic-template: vc.{dataset}.events
    partitions: 6
    replication-factor: 3
    retention-ms: -1
    compaction: false

  projection:
    retry:
      max-attempts: 10
      initial-interval: 1000
      multiplier: 2.0
      max-interval: 60000

  materialized-views:
    max-branches: 100
    monitoring:
      enabled: true
      interval-seconds: 300

projector:
  deduplication:
    enabled: true
    cache-size: 10000
  kafka-listener:
    enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

---

## Related Documentation

- [Kafka Storage Guide](./kafka-storage-guide.md) - Kafka topic management
- [Runbook](./runbook.md) - Operational procedures
- [CQRS Guide](../architecture/cqrs-event-sourcing.md) - Architecture details
- [Performance Guide](./performance.md) - Performance tuning

---

**Last Updated:** 2025-10-28
