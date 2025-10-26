# Dataset Management with Kafka Topic Integration

This folder contains tasks for implementing dynamic dataset creation and deletion with automatic Kafka topic management.

---

## Overview

**Problem:** Currently, Kafka topics are never created in production code. Users cannot dynamically create/delete datasets without DevOps intervention.

**Solution:** Implement explicit dataset lifecycle management with automatic Kafka topic creation/deletion.

**Status:** 🟢 Production Ready (Tasks 01-04 + 06 completed)
- ✅ Dataset creation endpoint with automatic Kafka topic creation
- ✅ Production Kafka configuration (RF=3, partitions=6)
- ✅ Robust error handling with retry logic and RFC 7807 responses
- ✅ Monitoring and metrics via Micrometer
- ✅ Topic health checks and manual healing (simplified implementation)
- 🔜 Future: Per-dataset tuning (Task 05)

---

## Task Breakdown

### Critical (Immediate)

#### ✅ [01 - Dataset Creation Endpoint](./01-dataset-creation-endpoint.md) - COMPLETED
**Priority:** Critical | **Time:** 4-5 hours | **Status:** ✅ DONE

Implement `POST /version/datasets/{name}` endpoint that:
- ✅ Creates Kafka topic automatically
- ✅ Creates initial commit and main branch
- ✅ Publishes `DatasetCreatedEvent`
- ✅ Validates dataset name (Kafka-compatible)

**Completed:** See commit 0ad6c89

**Deliverables:**
- ✅ `DatasetController` with POST endpoint
- ✅ `CreateDatasetCommand` and handler
- ✅ Kafka topic creation using `AdminClient`
- ✅ Integration tests
- ✅ OpenAPI documentation

---

#### ✅ [02 - Production Kafka Config](./02-production-kafka-config.md) - COMPLETED
**Priority:** Critical | **Time:** 2-3 hours | **Status:** ✅ DONE

Update Kafka configuration for production:
- ✅ Increase replication factor: 1 → 3 (fault tolerance)
- ✅ Increase partitions: 3 → 6 (scalability)
- ✅ Add `min.insync.replicas: 2`
- ✅ Configure producer durability (`acks=all`)
- ✅ Configure consumer isolation (`read_committed`)

**Completed:** See commit 0ad6c89

**Deliverables:**
- ✅ Updated `application-prod.yml`
- ✅ Environment-specific profiles (dev/prod)
- ✅ Updated `KafkaConfig` with production settings
- ✅ Updated integration tests
- ✅ Deployment documentation

---

### Important (Short-term)

#### ✅ [03 - Error Handling](./03-error-handling.md) - COMPLETED
**Priority:** Important | **Time:** 3-4 hours | **Status:** ✅ DONE

Implement robust error handling for topic creation:
- ✅ Handle topic-already-exists (idempotent)
- ✅ Handle Kafka unavailable (retry with backoff)
- ✅ Handle authorization failures (alert ops)
- ✅ Handle quota exceeded (clear user message)
- ✅ Rollback on partial failures

**Completed:** See commit 353ee54

**Deliverables:**
- ✅ Custom exception hierarchy
- ✅ `GlobalExceptionHandler` mappings (RFC 7807)
- ✅ Retry logic with Spring Retry
- ✅ Rollback mechanism
- ✅ Health check endpoint
- ✅ Unit and integration tests

---

#### ✅ [04 - Monitoring & Metrics](./04-monitoring-metrics.md) - COMPLETED
**Priority:** Important | **Time:** 3-4 hours | **Status:** ✅ DONE

Add comprehensive monitoring:
- ✅ Dataset lifecycle metrics (created, deleted, failures)
- ✅ Kafka topic metrics (created, deleted, count)
- ✅ Kafka health metrics (broker count, connectivity)
- ⏭️ Consumer lag metrics (skipped - not over-engineered)
- ⏭️ Prometheus alerts and Grafana dashboard (future)

**Completed:** See commit [pending]

**Deliverables:**
- ✅ Micrometer metrics in command handlers and exception handlers
- ✅ Essential metrics without over-engineering
- ⏭️ `KafkaMetrics` component (future enhancement)
- ⏭️ Custom metrics endpoint (future enhancement)
- ⏭️ Prometheus alert rules (future enhancement)
- ⏭️ Grafana dashboard JSON (future enhancement)

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

#### ✅ [06 - Topic Health Checks](./06-topic-health-checks.md) - COMPLETED (Simplified)
**Priority:** Future | **Time:** 4-5 hours | **Status:** ✅ DONE (simplified)

Simplified health checks and manual healing (no auto-healing or scheduled checks):
- ✅ Detect missing topics (via health endpoint)
- ✅ Manual topic recreation (via heal endpoint)
- ⏭️ Under-replicated partition detection (future)
- ⏭️ Configuration drift detection (future)
- ⏭️ Scheduled health checks (future)
- ⏭️ Auto-healing (future)

**Completed:** See commit [pending]

**Use Cases:**
- Operational visibility via health endpoint
- Manual healing when topics are accidentally deleted

**Deliverables:**
- ✅ `KafkaTopicHealthChecker` service (check topic existence)
- ✅ `TopicHealer` service with safeguards (manual healing only)
- ✅ `/actuator/kafka/health-detailed` endpoint
- ✅ `/actuator/kafka/topics/{dataset}/health` endpoint
- ✅ `/actuator/kafka/topics/{dataset}/heal` endpoint (requires confirmation)
- ✅ Integration tests
- ⏭️ Scheduled checks (intentionally skipped - no over-engineering)
- ⏭️ Auto-healing (intentionally skipped - no over-engineering)

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
- [x] Users can create datasets via `POST /version/datasets/{name}`
- [x] Kafka topics created automatically with correct configuration
- [ ] Users can delete datasets via `DELETE /version/datasets/{name}` (future)
- [ ] Kafka topics optionally deleted (configurable) (future)
- [x] Topic creation failures handled gracefully with clear error messages
- [x] Configuration validated (partition count, RF, retention)

### Non-Functional
- [x] Replication factor = 3 in production (fault tolerance)
- [x] Topics created with correct settings (retention, compaction, etc.)
- [x] Metrics track dataset/topic lifecycle
- [x] Health checks detect Kafka connectivity (via KafkaHealthIndicator)
- [x] Health checks detect missing/unhealthy topics (via `/actuator/kafka/health-detailed`)
- [x] Manual healing can recreate missing topics (via `/actuator/kafka/topics/{dataset}/heal?confirm=true`)
- [ ] Scheduled health checks (future enhancement)
- [ ] Auto-healing (future enhancement)

### Quality
- [x] All tests pass (unit + integration) - ~1115 tests passing
- [x] Zero Checkstyle/SpotBugs/PMD violations
- [x] Zero compiler warnings
- [x] OpenAPI documentation complete
- [ ] Architecture documentation updated (pending - C4 Level 3)

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
