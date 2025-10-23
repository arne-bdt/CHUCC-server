# Exception Handling - Remaining Tasks

## Status

**Projector-Side (Read Model): ✅ COMPLETED**
- ✅ Manual commit configuration implemented (2025-10-23, commits `5df63e0`, `039c072`)
- ✅ Exception rethrowing verified and tested
- ✅ Documentation completed (ADR-0003, Javadoc, CQRS guide)
- ✅ Audit completed (see [audit-findings.md](./audit-findings.md))

**Command-Side: ⚠️ CRITICAL ISSUE REMAINS**
- ❌ Fire-and-forget pattern causes silent data loss
- ⚠️ See Task 04 below for details

---

## Completed Work

### ✅ Task 01: Fix Kafka Manual Commit Configuration
**Completed:** 2025-10-23 (commits `5df63e0`, `039c072`)

**What was done:**
- Disabled auto-commit in KafkaConfig (`ENABLE_AUTO_COMMIT_CONFIG = false`)
- Added `AckMode.RECORD` to commit offset only after successful processing
- Created ReadModelProjectorExceptionHandlingTest (3 unit tests)
- Updated documentation (ADR-0003, ReadModelProjector Javadoc)

**Result:** Eliminated race condition where failed events could be skipped.

### ✅ Task 02: Add Documentation
**Completed:** 2025-10-23 (commit `039c072`)

**What was done:**
- Enhanced ReadModelProjector.java class Javadoc with exception handling strategy
- Created ADR-0003: Projector Fail-Fast Exception Handling
- Updated docs/architecture/cqrs-event-sourcing.md with exception handling section
- Documented Kafka configuration in KafkaConfig.java

**Result:** Future developers understand why exceptions are rethrown and how to maintain this behavior.

### ✅ Task 03: Audit Codebase for Exception Handling
**Completed:** 2025-10-23

**What was done:**
- Searched for all `@KafkaListener` annotations
- Reviewed exception handling patterns across codebase
- Audited event publishers, command handlers, controllers
- Documented findings in [audit-findings.md](./audit-findings.md)

**Result:** Identified critical command-side issue (Task 04).

### ~~Task 05: Fix Kafka Auto-Commit~~ (Obsolete)
**Reason:** Duplicate of Task 01 (already completed).

---

## Remaining Tasks

### ⚠️ Task 04: Fix Command Handler Silent Failures (CRITICAL)

**Status:** **NOT STARTED**

**Priority:** **CRITICAL** - This is a data loss bug affecting all write operations.

**Issue Summary:**
Command handlers use fire-and-forget pattern with swallowed exceptions:
```java
// ❌ CURRENT (BROKEN):
eventPublisher.publish(event)
    .exceptionally(ex -> {
      logger.error("Failed to publish", ex);
      return null;  // Swallows exception
    });
return event;  // Returns immediately, before Kafka confirms
```

**Impact:**
- Client receives HTTP 200 OK even if Kafka publishing fails
- Event never reaches Kafka → Read model never updated
- **Silent data loss** - no retry, no recovery

**Solution:**
```java
// ✅ CORRECT:
try {
  eventPublisher.publish(event).get();  // Wait for Kafka confirmation
  return event;
} catch (InterruptedException | ExecutionException ex) {
  logger.error("Failed to publish event to Kafka", ex);
  throw new IllegalStateException("Failed to publish event to Kafka", ex);
}
```

**Files Affected:**
- `src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java` (lines 165-176)
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java` (lines 138-146)
- All command handlers using `GraphCommandUtil.finalizeAndPublishGraphCommand()`

**Estimated Time:** 3-4 hours

**See:** [04-fix-command-handler-exceptions.md](./04-fix-command-handler-exceptions.md) for full details

---

## Success Criteria

### Completed (Projector-Side)
- [x] Kafka offset NOT committed on projection failure
- [x] Manual commit configuration (AckMode.RECORD)
- [x] Exception rethrowing tested and verified
- [x] Documentation complete (ADR, Javadoc, CQRS guide)
- [x] Codebase audit completed

### Remaining (Command-Side)
- [ ] Command handlers wait for Kafka confirmation (no fire-and-forget)
- [ ] Failed Kafka publishes return HTTP 500 (not 200 OK)
- [ ] Integration tests verify Kafka failure scenarios
- [ ] Documentation explains command-side exception handling
- [ ] All tests pass (currently ~1018 tests)
- [ ] Zero quality violations

---

## References

### Completed Work
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - ✅ Correct exception handling
- [KafkaConfig.java](../../src/main/java/org/chucc/vcserver/config/KafkaConfig.java) - ✅ Manual commit configuration
- [ADR-0003](../../docs/architecture/decisions/0003-projector-fail-fast-exception-handling.md) - ✅ Architecture decision
- [CQRS Guide](../../docs/architecture/cqrs-event-sourcing.md) - ✅ Updated with exception handling
- [Audit Findings](./audit-findings.md) - ✅ Complete audit results

### Remaining Work
- [GraphCommandUtil.java](../../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java) - ❌ Needs fix
- [CreateCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java) - ❌ Needs fix
- [Task 04 Details](./04-fix-command-handler-exceptions.md) - ⚠️ Critical fix required

### External Documentation
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [CompletableFuture Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html)

---

## Implementation History

- **2025-10-23:** Projector-side exception handling completed
  - Fixed Kafka manual commit configuration
  - Added comprehensive documentation
  - Completed codebase audit
  - Identified command-side critical issue (Task 04)
