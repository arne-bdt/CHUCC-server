# Recommended Specialized Agents for CHUCC Server

**Analysis of which specialized agents would improve development efficiency**

Date: 2025-10-09

---

## Overview

This project has specific architectural patterns (CQRS, Event Sourcing, test isolation) that require careful adherence. Specialized agents can help ensure compliance and catch common mistakes early.

---

## High-Value Agents (Recommended)

### 1. CQRS Compliance Checker

**Purpose:** Verify adherence to CQRS + Event Sourcing architecture patterns

**When to invoke:** After implementing or modifying command handlers, projectors, or controllers

**Checks:**
- ✅ Command handlers create events (don't update repositories directly)
- ✅ Projectors only update repositories (no business logic)
- ✅ Controllers return HTTP responses immediately (don't wait for projection)
- ✅ Events are immutable (Java records, no setters)
- ✅ Projector methods are idempotent (can replay safely)
- ✅ All state changes create events (no "silent" updates)
- ✅ Business logic in command handlers, not projectors

**Example violations to catch:**
```java
// ❌ BAD: Command handler updating repository directly
public class PutGraphCommandHandler {
  public CommitCreatedEvent handle(PutGraphCommand cmd) {
    var event = new CommitCreatedEvent(...);
    commitRepository.save(event.commit());  // ❌ Should not do this!
    return event;
  }
}

// ❌ BAD: Controller waiting for projection
public ResponseEntity<Void> put(...) {
  var event = handler.handle(command);
  eventPublisher.publish(event).get();  // ❌ Blocking!
  await().until(() -> repo.exists(id)); // ❌ Waiting for projection!
  return ResponseEntity.ok().build();
}

// ❌ BAD: Business logic in projector
@KafkaListener
public void handleCommitCreated(CommitCreatedEvent event) {
  if (event.author() == null) {  // ❌ Validation in projector!
    throw new IllegalArgumentException("Author required");
  }
  repository.save(event.commit());
}
```

**Agent configuration:**
```typescript
{
  name: "cqrs-compliance-checker",
  description: "Verify CQRS + Event Sourcing architecture compliance",
  triggers: [
    "after implementing command handler",
    "after modifying projector",
    "after adding new endpoint"
  ],
  checks: [
    "command_handlers_create_events_only",
    "projectors_update_repositories_only",
    "controllers_return_immediately",
    "events_are_immutable",
    "projectors_are_idempotent",
    "no_business_logic_in_projectors"
  ]
}
```

**Value:** ⭐⭐⭐⭐⭐ (Critical - catches architecture violations early)

---

### 2. Test Isolation Validator

**Purpose:** Verify correct test patterns (projector on/off, await usage)

**When to invoke:** After writing or modifying integration tests

**Checks:**
- ✅ API layer tests don't enable projector (default behavior)
- ✅ API layer tests don't query repositories
- ✅ API layer tests don't use `await()`
- ✅ Projector tests enable projector via `@TestPropertySource`
- ✅ Projector tests use `await()` for async verification
- ✅ Tests extend `IntegrationTestFixture` (correct base class)
- ✅ Test class names follow conventions (*IT, *Test)

**Example violations to catch:**
```java
// ❌ BAD: API layer test querying repository
@SpringBootTest
@ActiveProfiles("it")
class GraphStoreControllerIT {
  @Test
  void putGraph_shouldUpdateRepository() {
    restTemplate.exchange("/data", PUT, ...);
    var commit = commitRepository.findById(id);  // ❌ Projector disabled!
    assertThat(commit).isPresent();  // ❌ Will fail!
  }
}

// ❌ BAD: Projector test without enabling projector
@SpringBootTest
@ActiveProfiles("it")
// Missing: @TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphEventProjectorIT {
  @Test
  void handleCommitCreated_shouldUpdateRepository() {
    eventPublisher.publish(event).get();
    await().until(() -> repo.exists(id));  // ❌ Will timeout!
  }
}

// ❌ BAD: Projector test without await()
@TestPropertySource(properties = "projector.kafka-listener.enabled=true")
class GraphEventProjectorIT {
  @Test
  void handleCommitCreated_shouldUpdateRepository() {
    eventPublisher.publish(event).get();
    var commit = repo.findById(id);  // ❌ Race condition!
    assertThat(commit).isPresent();
  }
}
```

**Agent configuration:**
```typescript
{
  name: "test-isolation-validator",
  description: "Verify correct test patterns for CQRS async architecture",
  triggers: [
    "after writing integration test",
    "after modifying test"
  ],
  checks: [
    "api_tests_dont_enable_projector",
    "api_tests_dont_query_repositories",
    "projector_tests_enable_projector",
    "projector_tests_use_await",
    "test_naming_conventions",
    "proper_test_annotations"
  ]
}
```

**Value:** ⭐⭐⭐⭐⭐ (Critical - test isolation is tricky and easy to get wrong)

---

### 3. Event Schema Evolution Checker

**Purpose:** Ensure event schema changes don't break event replay

**When to invoke:** After modifying event classes or adding new event types

**Checks:**
- ✅ Existing event fields not removed (breaking change)
- ✅ New fields have defaults or are nullable (backward compatible)
- ✅ Event class is a record (immutability)
- ✅ All event types extend `VersionControlEvent`
- ✅ Projector handles new event type (if added)
- ✅ Event serialization/deserialization tested
- ✅ No breaking changes to existing events in production

**Example violations to catch:**
```java
// ❌ BAD: Removing field from existing event
public record CommitCreatedEvent(
  String dataset,
  CommitId commitId,
  // String author,  ← ❌ Removed! Breaks replay of old events!
  String message,
  Instant timestamp
) { }

// ❌ BAD: Adding non-nullable field without default
public record CommitCreatedEvent(
  String dataset,
  CommitId commitId,
  String author,
  String message,
  Instant timestamp,
  String newRequiredField  // ❌ Old events don't have this!
) { }

// ✅ GOOD: Adding nullable field (backward compatible)
public record CommitCreatedEvent(
  String dataset,
  CommitId commitId,
  String author,
  String message,
  Instant timestamp,
  String optionalNewField  // ✅ Nullable, backward compatible
) { }
```

**Agent configuration:**
```typescript
{
  name: "event-schema-evolution-checker",
  description: "Verify event schema changes are backward compatible",
  triggers: [
    "after modifying event class",
    "after adding new event type"
  ],
  checks: [
    "no_removed_fields",
    "new_fields_are_nullable_or_have_defaults",
    "events_are_immutable_records",
    "events_extend_base_interface",
    "projector_handles_new_event",
    "serialization_tests_exist"
  ]
}
```

**Value:** ⭐⭐⭐⭐ (Important - prevents production replay failures)

---

### 4. Documentation Sync Agent

**Purpose:** Keep architecture documentation synchronized with code changes

**When to invoke:** After significant changes to architecture, components, or patterns

**Checks:**
- ✅ C4 diagrams reflect actual component structure
- ✅ Architecture docs mention new components
- ✅ CQRS guide has up-to-date examples
- ✅ Package count in docs matches actual count
- ✅ Test count in CLAUDE.md is current
- ✅ New endpoints documented in OpenAPI spec
- ✅ New event types documented in architecture docs

**Example sync tasks:**
```markdown
## Changes detected:

1. New component added: SnapshotService
   → Update: docs/architecture/c4-level3-component.md
   → Add to service list (currently 12 services)

2. Test count increased: 911 → 945
   → Update: .claude/CLAUDE.md (line 183)

3. New event type: SnapshotCreatedEvent
   → Update: docs/architecture/c4-level1-context.md (event types list)
   → Update: docs/architecture/cqrs-event-sourcing.md (add example)

4. New controller: SnapshotController
   → Update: docs/architecture/c4-level3-component.md (controller list)
```

**Agent configuration:**
```typescript
{
  name: "documentation-sync-agent",
  description: "Keep architecture documentation synchronized with code",
  triggers: [
    "after adding component",
    "after architectural change",
    "before completing large task"
  ],
  checks: [
    "c4_diagrams_are_current",
    "component_counts_match",
    "test_counts_are_current",
    "new_components_documented",
    "examples_are_valid",
    "openapi_spec_is_current"
  ]
}
```

**Value:** ⭐⭐⭐⭐ (Important - prevents documentation drift)

---

## Medium-Value Agents (Optional)

### 5. SPARQL Protocol Compliance Checker

**Purpose:** Verify SPARQL 1.2 Protocol compliance

**When to invoke:** After implementing or modifying SPARQL endpoints

**Checks:**
- ✅ Content negotiation implemented correctly
- ✅ Selector resolution (branch, commit, asOf) works
- ✅ HTTP status codes match spec
- ✅ Error responses use RFC 7807 format
- ✅ ETags generated correctly
- ✅ Query parameters validated
- ✅ RDF serialization formats supported

**Value:** ⭐⭐⭐ (Useful - but existing tests cover most of this)

---

### 6. Performance Regression Detector

**Purpose:** Detect performance regressions in build times and test execution

**When to invoke:** After significant code changes or refactoring

**Checks:**
- ✅ Build time hasn't increased significantly (baseline: 2-3 min)
- ✅ Test execution time reasonable (<5 min for full suite)
- ✅ Static analysis time reasonable (<30 sec)
- ✅ No N+1 query patterns in projectors
- ✅ No excessive object allocations in hot paths

**Value:** ⭐⭐⭐ (Useful - but manual monitoring may suffice for now)

---

## Low-Value Agents (Not Recommended)

### ❌ Code Style Enforcer

**Why not needed:** Checkstyle, SpotBugs, PMD already handle this automatically

### ❌ Test Coverage Calculator

**Why not needed:** JaCoCo already provides coverage reports

### ❌ Dependency Updater

**Why not needed:** Maven versions plugin handles this

---

## Recommended Implementation Priority

### Phase 1: Critical Agents (Implement First)

1. **CQRS Compliance Checker** - Most critical for architecture integrity
2. **Test Isolation Validator** - Prevents flaky tests and confusion

### Phase 2: Important Agents (Implement Soon)

3. **Event Schema Evolution Checker** - Prevents production issues
4. **Documentation Sync Agent** - Prevents doc drift

### Phase 3: Optional Agents (Implement Later)

5. **SPARQL Protocol Compliance Checker** - Nice to have
6. **Performance Regression Detector** - Monitor manually for now

---

## How to Use These Agents

### Integration Points

**In CLAUDE.md:**
```markdown
## Specialized Agents

After implementing features, invoke specialized agents for verification:

1. After command/projector changes:
   - Run: `@cqrs-compliance-checker` agent

2. After writing tests:
   - Run: `@test-isolation-validator` agent

3. After modifying events:
   - Run: `@event-schema-evolution-checker` agent

4. Before completing large tasks:
   - Run: `@documentation-sync-agent` agent
```

**In Development Workflow:**
```markdown
### During Implementation

1. Use `-q` for all Maven commands
2. Run Phase 1 (static analysis) before tests
3. Write tests first (TDD)
4. Run Phase 2a (incremental) after implementation
5. **Invoke specialized agents for verification**  ← NEW
6. Run Phase 2b (full build) when ready to complete
```

---

## Example: Using CQRS Compliance Checker

### Scenario

You just implemented a new command handler for cherry-pick operation:

```java
// New file: CherryPickCommandHandler.java
@Component
public class CherryPickCommandHandler
    implements CommandHandler<CherryPickCommand> {

  @Override
  public CherryPickedEvent handle(CherryPickCommand command) {
    // Implementation...
  }
}
```

### Invoke Agent

```
Me: I've just implemented CherryPickCommandHandler. Please run the
CQRS compliance checker to verify architecture compliance.

Agent (CQRS Compliance Checker):
Analyzing CherryPickCommandHandler.java...

✅ Command handler creates event (CherryPickedEvent)
✅ No direct repository updates
✅ Event is immutable (record type)
✅ Projector handles CherryPickedEvent (found in ReadModelProjector)
✅ Business logic in command handler (not projector)

⚠️ Warning: Consider adding comment explaining cherry-pick logic

✅ CQRS compliance verified - no violations found
```

---

## Summary

### Highest Value Agents

1. **CQRS Compliance Checker** - Architecture integrity ⭐⭐⭐⭐⭐
2. **Test Isolation Validator** - Test correctness ⭐⭐⭐⭐⭐
3. **Event Schema Evolution Checker** - Production safety ⭐⭐⭐⭐
4. **Documentation Sync Agent** - Documentation accuracy ⭐⭐⭐⭐

### Implementation Recommendation

**Start with agents 1-2** (CQRS + Test Isolation) as these catch the most critical issues that are:
- Hard to detect manually
- Easy to get wrong
- Have significant impact if wrong

**Add agents 3-4** once the first two are working well.

### Alternative: Manual Checklists

If specialized agents are not available or too complex to configure, create **manual checklists** in `.claude/checklists/`:

- `cqrs-compliance-checklist.md`
- `test-isolation-checklist.md`
- `event-schema-checklist.md`
- `documentation-sync-checklist.md`

AI agents can then reference these checklists when reviewing code.

---

## References

- [Architecture Overview](../../docs/architecture/README.md)
- [CQRS + Event Sourcing Guide](../../docs/architecture/cqrs-event-sourcing.md)
- [C4 Component Diagram](../../docs/architecture/c4-level3-component.md)
- [Testing Guidelines](../CLAUDE.md#testing-guidelines)
