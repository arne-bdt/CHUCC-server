# Exception Handling Audit & Fixes

## Goal

Verify and fix exception handling across the entire CQRS architecture to prevent silent data loss and read model inconsistencies.

## Background

**Critical Requirement:** Both command and query sides MUST handle failures correctly:

**Read Side (Projector):**
- ✅ Rethrow exceptions (trigger Kafka retry/DLQ)
- ✅ NOT commit the Kafka offset on failure
- ✅ Prevent silent read model inconsistency

**Command Side (Handlers):**
- ✅ Wait for Kafka confirmation before returning
- ✅ Propagate publish failures to HTTP layer (return 500, not 200)
- ✅ Prevent silent data loss (client thinks operation succeeded but event never published)

**Why this matters:**
- Silent failures create **permanent inconsistencies**
- Read model diverges from event stream with no recovery mechanism
- Data corruption is undetectable until queries return wrong results

## Audit Results

✅ **Read Side:** `ReadModelProjector` correctly rethrows exceptions - **NO ISSUES**
❌ **Command Side:** Multiple handlers use fire-and-forget pattern - **CRITICAL ISSUES FOUND**

See [audit-findings.md](./audit-findings.md) for full details.

## Task Breakdown

1. **[01-verify-exception-rethrowing.md](./01-verify-exception-rethrowing.md)**
   - Write tests that verify projector exceptions trigger retries
   - Test Kafka offset is NOT committed on failure
   - Test successful retry after transient failure

2. **[02-add-documentation.md](./02-add-documentation.md)**
   - Document exception handling strategy in code
   - Add architectural decision record (ADR)
   - Update CQRS guide with failure handling

3. **[03-audit-similar-patterns.md](./03-audit-similar-patterns.md)** ✅ COMPLETED
   - Search codebase for similar event handling patterns
   - Verify all event processors use proper exception handling
   - **RESULT:** Found critical issues in command handlers (see task 04)

4. **[04-fix-command-handler-exceptions.md](./04-fix-command-handler-exceptions.md)** ⚠️ **CRITICAL**
   - Fix fire-and-forget pattern in `GraphCommandUtil`
   - Fix fire-and-forget pattern in `CreateCommitCommandHandler`
   - Add integration tests for Kafka failure scenarios
   - Prevent silent data loss on command side

5. **[05-fix-kafka-auto-commit.md](./05-fix-kafka-auto-commit.md)** ⚠️ **IMPROVEMENT**
   - Disable Kafka auto-commit (use manual commit)
   - Set ACK mode to RECORD (commit only on success)
   - Eliminate race condition in projector offset commit

## Success Criteria

**Read Side (Projector):**
- [ ] Tests prove that failed events trigger retries (not silent failures)
- [ ] Tests verify Kafka offset is NOT committed on projection failure
- [ ] Documentation explains projector exception handling strategy

**Command Side (Handlers):**
- [ ] Command handlers wait for Kafka confirmation (no fire-and-forget)
- [ ] Failed Kafka publishes return HTTP 500 (not 200 OK)
- [ ] Integration tests verify Kafka failure scenarios
- [ ] Documentation explains command-side exception handling

**Overall:**
- [x] Codebase audit completed (found command-side issues)
- [ ] All exception handling issues fixed
- [ ] Zero Checkstyle/SpotBugs/PMD violations
- [ ] All tests pass

## Priority

1. **CRITICAL:** Fix command handler fire-and-forget (Task 04) - **DATA LOSS BUG**
2. **HIGH:** Add documentation (Task 02) - ✅ **COMPLETED** - Prevent future regressions
3. **MEDIUM:** Fix Kafka auto-commit (Task 05) - Eliminate rare race condition
4. **MEDIUM:** Add projector failure tests (Task 01) - Already correct, just verify

## References

- [Audit Findings Report](./audit-findings.md) - Comprehensive analysis
- [ReadModelProjector.java](../../src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java) - ✅ Correct
- [GraphCommandUtil.java](../../src/main/java/org/chucc/vcserver/util/GraphCommandUtil.java) - ❌ Needs fix
- [CreateCommitCommandHandler.java](../../src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java) - ❌ Needs fix
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [Spring Kafka Error Handling](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
