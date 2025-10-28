# Operational Runbook

This runbook provides procedures for diagnosing and resolving common operational issues in CHUCC Server.

---

## Table of Contents

- [Projection Failure Recovery](#projection-failure-recovery)
- [Dataset Management Issues](#dataset-management-issues)
- [Kafka Topic Issues](#kafka-topic-issues)
- [Performance Degradation](#performance-degradation)

---

## Projection Failure Recovery

### Symptom: Events in Dead Letter Queue

**Detection:**
- Health endpoint shows DLQ messages: `GET /actuator/health`
- Metrics alert: `chucc.projection.events.total{status="error"} > 0`
- Logs show: `Failed to project event ... sent to DLQ`

**Diagnosis:**

1. **Check DLQ messages:**
```bash
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic vc.default.events.dlq \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

2. **Identify failure cause:**
   - Application logs: `grep "Failed to project event" logs/application.log`
   - Exception type: `ProjectionException`, `RdfPatchException`, etc.
   - Stack trace shows exact line where failure occurred

3. **Check metrics:**
```bash
# Error rate by event type
curl http://localhost:3030/actuator/prometheus | grep chucc_projection_events_total

# Retry attempts
curl http://localhost:3030/actuator/prometheus | grep chucc_projection_retries_total
```

**Common Causes:**

| Error Type | Root Cause | Fix |
|------------|------------|-----|
| `RdfPatchException` | Malformed RDF patch in event | Fix patch format, republish event |
| `RepositoryException` | Repository corruption | Restart app (replay events) |
| `NullPointerException` | Application bug | Deploy bug fix, replay DLQ |
| `OutOfMemoryError` | Insufficient heap | Increase JVM heap, restart |
| `ConcurrentModificationException` | Race condition | Check projector concurrency settings |
| `IllegalStateException: Missing commit` | Test cleanup race or data corruption | Check commit repository integrity |

**Resolution:**

#### Option 1: Fix and Replay (Recommended)

This is the safest and most common approach:

1. **Identify the root cause** from logs/DLQ messages
2. **Fix the issue:**
   - Application bug → Deploy fixed version
   - Configuration issue → Update configuration
   - Data corruption → Restore from backup if needed
3. **Restart the application:**
   ```bash
   # Application will automatically replay all events including DLQ
   systemctl restart chucc-server
   ```
4. **Verify recovery:**
   ```bash
   # Check health endpoint
   curl http://localhost:3030/actuator/health

   # Verify DLQ is empty
   kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic vc.default.events.dlq \
     --from-beginning \
     --timeout-ms 5000
   ```

#### Option 2: Skip Poison Event (Use with Caution)

Only use when event is truly unrecoverable and can be safely ignored:

1. **Document the decision:**
   ```bash
   # Record in audit log
   echo "$(date): Skipping poison event <event-id> due to <reason>" >> /var/log/chucc-skipped-events.log
   ```
2. **Manually advance offset:**
   ```bash
   # This is an advanced operation requiring Kafka admin tools
   # Consult Kafka documentation for offset management
   kafka-consumer-groups.sh \
     --bootstrap-server localhost:9092 \
     --group chucc-server-projector \
     --topic vc.default.events \
     --reset-offsets \
     --to-offset <next-offset> \
     --execute
   ```
3. **Verify system recovers** and document why event was skipped

#### Option 3: Manual Republish (Complex)

When event needs fixing before replay:

1. **Extract event from DLQ:**
   ```bash
   kafka-console-consumer.sh \
     --bootstrap-server localhost:9092 \
     --topic vc.default.events.dlq \
     --from-beginning \
     --max-messages 1 > poison-event.json
   ```
2. **Fix event payload** (edit JSON file)
3. **Republish to main topic:**
   ```bash
   # Requires custom tooling or Kafka producer
   kafka-console-producer.sh \
     --bootstrap-server localhost:9092 \
     --topic vc.default.events \
     < fixed-event.json
   ```
4. **Verify projection succeeds** by checking logs

**Prevention:**

- **Schema validation:** Add event schema validation before publishing
- **Integration tests:** Test all event types thoroughly
- **Staging environment:** Test changes in staging before production
- **Monitoring:** Alert on DLQ messages immediately
- **Code review:** Focus on projector logic and event handling

---

## Dataset Management Issues

### Symptom: Dataset Creation Fails

**Detection:**
- `POST /version/datasets/{name}` returns 500 error
- Logs show: `Failed to create Kafka topic`

**Diagnosis:**

1. **Check Kafka connectivity:**
   ```bash
   kafka-broker-api-versions.sh --bootstrap-server localhost:9092
   ```

2. **Check Kafka disk space:**
   ```bash
   df -h /var/lib/kafka
   ```

3. **Verify replication factor feasible:**
   ```bash
   # If RF=3 but only 2 brokers available, creation will fail
   kafka-broker-api-versions.sh --bootstrap-server localhost:9092 | grep "id:"
   ```

**Resolution:**

1. **Fix Kafka issues** (add brokers, free disk space, etc.)
2. **Retry dataset creation** (endpoint is idempotent)
3. **If topic partially created, check health:**
   ```bash
   curl http://localhost:3030/actuator/kafka/health-detailed?dataset=mydata
   ```
4. **Heal if needed:**
   ```bash
   curl -X POST "http://localhost:3030/actuator/kafka/topics/mydata/heal?confirm=true"
   ```

---

## Kafka Topic Issues

### Symptom: Topic Health Check Fails

**Detection:**
- Health endpoint shows topic issues: `GET /actuator/kafka/health-detailed?dataset=mydata`
- Topic missing or has wrong configuration

**Resolution:**

1. **Check if topic exists:**
   ```bash
   kafka-topics.sh --list --bootstrap-server localhost:9092 | grep mydata
   ```

2. **If missing, recreate:**
   ```bash
   curl -X POST "http://localhost:3030/actuator/kafka/topics/mydata/heal?confirm=true"
   ```

3. **If exists but misconfigured, manual fix:**
   ```bash
   # Update replication factor (requires Kafka tools)
   kafka-reassign-partitions.sh --bootstrap-server localhost:9092 \
     --reassignment-json-file increase-replication.json \
     --execute
   ```

4. **Verify health:**
   ```bash
   curl http://localhost:3030/actuator/kafka/health-detailed?dataset=mydata
   ```

---

## Performance Degradation

### Symptom: Slow Query Response Times

**Detection:**
- Query latency > 500ms
- Metrics show high cache miss rate

**Diagnosis:**

1. **Check materialized view cache:**
   ```bash
   curl http://localhost:3030/actuator/health/materializedViews
   ```

2. **Check cache metrics:**
   ```bash
   curl http://localhost:3030/actuator/prometheus | grep materialized_branch_cache
   ```

3. **Check heap usage:**
   ```bash
   curl http://localhost:3030/actuator/metrics/jvm.memory.used
   ```

**Resolution:**

1. **If cache eviction too aggressive, increase max branches:**
   ```yaml
   # application.yml
   chucc:
     materialized-views:
       max-branches: 50  # Increase from default 25
   ```

2. **If cache miss rate high, rebuild graphs:**
   ```bash
   # Rebuild specific branch
   curl -X POST "http://localhost:3030/actuator/materialized-views/rebuild?dataset=mydata&branch=main"

   # Rebuild all branches
   curl -X POST "http://localhost:3030/actuator/materialized-views/rebuild"
   ```

3. **If OOM errors, increase heap:**
   ```bash
   # Update JVM args
   java -Xms2g -Xmx4g -jar vc-server.jar
   ```

4. **Monitor recovery:**
   ```bash
   # Watch cache hit rate improve
   watch -n 5 'curl -s http://localhost:3030/actuator/prometheus | grep hit_rate'
   ```

---

## Emergency Procedures

### Complete System Recovery

When system is completely unresponsive:

1. **Stop application:**
   ```bash
   systemctl stop chucc-server
   ```

2. **Check Kafka health:**
   ```bash
   kafka-broker-api-versions.sh --bootstrap-server localhost:9092
   ```

3. **Clear application state (if needed):**
   ```bash
   # Only do this if data corruption suspected
   rm -rf /var/lib/chucc-server/cache/*
   ```

4. **Restart with verbose logging:**
   ```bash
   # Update logback.xml to DEBUG level
   systemctl start chucc-server
   tail -f /var/log/chucc-server/application.log
   ```

5. **Verify event replay:**
   ```bash
   # Watch projector logs
   grep "Processing.*Event" /var/log/chucc-server/application.log | tail -50
   ```

6. **Check health endpoints:**
   ```bash
   curl http://localhost:3030/actuator/health
   curl http://localhost:3030/actuator/health/materializedViews
   ```

---

## Useful Commands

### Health Checks

```bash
# Overall health
curl http://localhost:3030/actuator/health

# Materialized views health
curl http://localhost:3030/actuator/health/materializedViews

# Kafka topic health
curl "http://localhost:3030/actuator/kafka/health-detailed?dataset=default"
```

### Metrics

```bash
# All metrics (Prometheus format)
curl http://localhost:3030/actuator/prometheus

# Projection metrics
curl http://localhost:3030/actuator/prometheus | grep chucc_projection

# Cache metrics
curl http://localhost:3030/actuator/prometheus | grep materialized_branch_cache

# Dataset metrics
curl http://localhost:3030/actuator/prometheus | grep dataset
```

### Kafka Operations

```bash
# List topics
kafka-topics.sh --list --bootstrap-server localhost:9092

# Describe topic
kafka-topics.sh --describe --topic vc.default.events --bootstrap-server localhost:9092

# Check consumer lag
kafka-consumer-groups.sh --describe --group chucc-server-projector --bootstrap-server localhost:9092

# Tail events
kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic vc.default.events \
  --from-beginning \
  --max-messages 10
```

---

## Escalation

If issues persist after following these procedures:

1. **Gather diagnostic information:**
   - Application logs (last 1000 lines)
   - Kafka logs (last 1000 lines)
   - Health endpoint outputs
   - Metrics snapshot
   - Recent git commits

2. **Create GitHub issue** with:
   - Symptom description
   - Steps taken
   - Diagnostic information
   - Expected vs actual behavior

3. **Contact support:** support@example.com

---

**Last Updated:** 2025-10-28
