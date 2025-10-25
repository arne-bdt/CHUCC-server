# Dataset Management with Kafka Topic Integration

This folder contains tasks for implementing dynamic dataset creation and deletion with automatic Kafka topic management.

---

## Overview

**Problem:** Currently, Kafka topics are never created in production code. Users cannot dynamically create/delete datasets without DevOps intervention.

**Solution:** Implement explicit dataset lifecycle management with automatic Kafka topic creation/deletion.

---

## Task Breakdown

### Critical (Immediate)

#### [01 - Dataset Creation Endpoint](./01-dataset-creation-endpoint.md)
**Priority:** Critical | **Time:** 4-5 hours

Implement `POST /version/datasets/{name}` endpoint that:
- Creates Kafka topic automatically
- Creates initial commit and main branch
- Publishes `DatasetCreatedEvent`
- Validates dataset name (Kafka-compatible)

**Why Critical:** Without this, users cannot create datasets dynamically.

**Deliverables:**
- `DatasetController` with POST endpoint
- `CreateDatasetCommand` and handler
- Kafka topic creation using `AdminClient`
- Integration tests
- OpenAPI documentation

---

#### [02 - Production Kafka Config](./02-production-kafka-config.md)
**Priority:** Critical | **Time:** 2-3 hours

Update Kafka configuration for production:
- Increase replication factor: 1 → 3 (fault tolerance)
- Increase partitions: 3 → 6 (scalability)
- Add `min.insync.replicas: 2`
- Configure producer durability (`acks=all`)
- Configure consumer isolation (`read_committed`)

**Why Critical:** Current config (RF=1) has no fault tolerance. Data loss will occur on broker failure.

**Deliverables:**
- Updated `application-prod.yml`
- Environment-specific profiles (dev/prod)
- Updated `KafkaConfig` with production settings
- Updated integration tests
- Deployment documentation

---

### Important (Short-term)

#### [03 - Error Handling](./03-error-handling.md)
**Priority:** Important | **Time:** 3-4 hours

Implement robust error handling for topic creation:
- Handle topic-already-exists (idempotent)
- Handle Kafka unavailable (retry with backoff)
- Handle authorization failures (alert ops)
- Handle quota exceeded (clear user message)
- Rollback on partial failures

**Why Important:** Topic creation can fail for many reasons. Without proper error handling, users get cryptic errors.

**Deliverables:**
- Custom exception hierarchy
- `GlobalExceptionHandler` mappings (RFC 7807)
- Retry logic with Spring Retry
- Rollback mechanism
- Health check endpoint
- Unit and integration tests

---

#### [04 - Monitoring & Metrics](./04-monitoring-metrics.md)
**Priority:** Important | **Time:** 3-4 hours

Add comprehensive monitoring:
- Dataset lifecycle metrics (created, deleted, failures)
- Kafka topic metrics (created, deleted, count)
- Kafka health metrics (broker count, connectivity)
- Consumer lag metrics
- Prometheus alerts and Grafana dashboard

**Why Important:** Without metrics, operators cannot detect issues like topic creation failures or consumer lag.

**Deliverables:**
- `KafkaMetrics` component with scheduled checks
- Micrometer metrics annotations
- Custom metrics endpoint (`/actuator/kafka`)
- Prometheus alert rules
- Grafana dashboard JSON
- Alerting runbook

---

### Future (Nice-to-Have)

#### [05 - Per-Dataset Tuning](./05-per-dataset-tuning.md)
**Priority:** Future | **Time:** 4-5 hours

Allow per-dataset Kafka configuration:
- Custom partition count
- Custom retention period
- Custom replication factor
- Custom compaction policy

**Use Cases:**
- Archive datasets: Finite retention, lower partitions
- Hot datasets: Higher partitions, more replicas
- Compliance datasets: Infinite retention, RF=5

**Deliverables:**
- Extended `CreateDatasetRequest` with `kafka` config
- Validation for custom configs
- Store config in `Dataset` entity
- `PATCH /datasets/{name}/config` endpoint
- Unit and integration tests

---

#### [06 - Topic Health Checks](./06-topic-health-checks.md)
**Priority:** Future | **Time:** 4-5 hours

Automated health checks and self-healing:
- Detect missing topics (auto-recreate)
- Detect under-replicated partitions (alert)
- Detect configuration drift (auto-correct)
- Detect orphan topics (alert for cleanup)

**Use Cases:**
- Topic accidentally deleted → Auto-recreate
- Config drift → Detect and fix
- Operational visibility

**Deliverables:**
- `KafkaHealthChecker` with scheduled checks
- `TopicHealer` service with safeguards
- `/actuator/kafka/health-detailed` endpoint
- `/actuator/kafka/topics/{dataset}/heal` endpoint
- Auto-healing configuration (disabled by default)
- Unit and integration tests

---

## Dependencies

```
Task 01 (Dataset Creation)
  ├─> Task 02 (Production Config)
  ├─> Task 03 (Error Handling)
  ├─> Task 04 (Monitoring)
  ├─> Task 05 (Per-Dataset Tuning)
  └─> Task 06 (Health Checks)
```

**Recommended Order:**
1. **01 + 02** (parallel) - Core functionality + production config
2. **03** - Error handling (depends on 01)
3. **04** - Monitoring (depends on 01-03)
4. **05** - Per-dataset tuning (optional, depends on 01)
5. **06** - Health checks (optional, depends on 01-04)

---

## Current Architecture

### Topic Structure

**Pattern:** One topic per dataset
- Dataset "mydata" → Topic `vc.mydata.events`
- Dataset "products" → Topic `vc.products.events`

**Why:** Isolation, independent scaling, easy deletion

### Partition Strategy

**Aggregate-based partition keys:**
- Branch events: `{dataset}:{branchName}` (e.g., `mydata:main`)
- Dataset events: `{dataset}` (e.g., `mydata`)
- Commit events: Inherit from branch

**Why:** Guaranteed ordering per aggregate (branch)

### Consumer Pattern

**Topic pattern matching:**
```java
@KafkaListener(topicPattern = "vc\\..*\\.events")
```

**Why:** Dynamically subscribe to new dataset topics without config changes

---

## Configuration Overview

### Current (Development)
```yaml
kafka:
  partitions: 3
  replication-factor: 1
  retention-ms: -1
```

### Target (Production)
```yaml
kafka:
  partitions: 6
  replication-factor: 3
  retention-ms: -1
  producer:
    acks: all
    enable-idempotence: true
  consumer:
    isolation-level: read_committed
```

---

## Acceptance Criteria (All Tasks)

### Functional
- [ ] Users can create datasets via `POST /version/datasets/{name}`
- [ ] Kafka topics created automatically with correct configuration
- [ ] Users can delete datasets via `DELETE /version/datasets/{name}`
- [ ] Kafka topics optionally deleted (configurable)
- [ ] Topic creation failures handled gracefully with clear error messages
- [ ] Configuration validated (partition count, RF, retention)

### Non-Functional
- [ ] Replication factor = 3 in production (fault tolerance)
- [ ] Topics created with correct settings (retention, compaction, etc.)
- [ ] Metrics track dataset/topic lifecycle
- [ ] Health checks detect missing/unhealthy topics
- [ ] Auto-healing can recreate missing topics (when enabled)

### Quality
- [ ] All tests pass (unit + integration)
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] Zero compiler warnings
- [ ] OpenAPI documentation complete
- [ ] Architecture documentation updated

---

## Testing Strategy

### Unit Tests
- Command handlers (dataset creation/deletion)
- Kafka topic creation/deletion logic
- Validation logic
- Error handling scenarios

### Integration Tests
- `DatasetCreationIT` - API layer (projector disabled)
- `DatasetProjectorIT` - Event projection (projector enabled)
- `DatasetDeletionIT` - Deletion flow
- `KafkaHealthCheckIT` - Health checks
- `TopicHealerIT` - Auto-healing

### Manual Tests (Production)
```bash
# 1. Create dataset
curl -X POST http://localhost:8080/version/datasets/test \
  -H "SPARQL-VC-Author: Admin <admin@example.org>"

# 2. Verify topic created
kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --describe --topic vc.test.events

# 3. Check health
curl http://localhost:8080/actuator/kafka/health-detailed

# 4. Delete dataset
curl -X DELETE http://localhost:8080/version/datasets/test?deleteKafkaTopic=true
```

---

## References

### Code References
- [EventPublisher.java](../../src/main/java/org/chucc/vcserver/event/EventPublisher.java) - Topic routing
- [KafkaProperties.java](../../src/main/java/org/chucc/vcserver/config/KafkaProperties.java) - Configuration
- [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java) - Kafka beans
- [DeleteDatasetCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/DeleteDatasetCommandHandler.java) - Topic deletion pattern
- [AggregateIdentity.java](../../src/main/java/org/chucc/vcserver/event/AggregateIdentity.java) - Partition keys

### Documentation
- [Architecture Overview](../../docs/architecture/README.md)
- [CQRS + Event Sourcing](../../docs/architecture/cqrs-event-sourcing.md)
- [Development Guidelines](../../.claude/CLAUDE.md)

### External
- [Kafka Topic Configuration](https://kafka.apache.org/documentation/#topicconfigs)
- [Kafka AdminClient API](https://kafka.apache.org/34/javadoc/org/apache/kafka/clients/admin/AdminClient.html)
- [Spring Kafka](https://docs.spring.io/spring-kafka/reference/html/)
- [Micrometer](https://micrometer.io/docs)

---

## Estimated Total Time

| Priority | Tasks | Time |
|----------|-------|------|
| **Critical** | 01-02 | 6-8 hours |
| **Important** | 03-04 | 6-8 hours |
| **Future** | 05-06 | 8-10 hours |
| **Total** | 6 tasks | **20-26 hours** |

**Minimum Viable Implementation:** Tasks 01-02 (6-8 hours)
**Production Ready:** Tasks 01-04 (12-16 hours)
**Full Implementation:** Tasks 01-06 (20-26 hours)

---

## Getting Started

1. **Read:** [Architecture Overview](../../docs/architecture/README.md)
2. **Read:** [CQRS + Event Sourcing](../../docs/architecture/cqrs-event-sourcing.md)
3. **Start with:** [01-dataset-creation-endpoint.md](./01-dataset-creation-endpoint.md)
4. **Then:** [02-production-kafka-config.md](./02-production-kafka-config.md)

**Questions?** Check the [Development Guidelines](../../.claude/CLAUDE.md)
