# Task 03: Audit Codebase for Similar Exception Handling Patterns

## Objective

Search the codebase for similar event handling patterns and verify they all use proper exception handling (rethrow, not swallow).

## Current State

- `ReadModelProjector` correctly rethrows exceptions
- Need to verify no other components silently swallow exceptions
- Need to check command handlers, event publishers, other consumers

## Implementation Steps

### 1. Search for Kafka Listeners

Find all `@KafkaListener` annotations:

```bash
grep -rn "@KafkaListener" src/
```

**Expected:**
- `ReadModelProjector.handleEvent()` - ✅ Already correct

**Action:** Verify each listener rethrows exceptions.

### 2. Search for Exception Handling Patterns

Find catch blocks that might swallow exceptions:

```bash
# Pattern 1: Catch without rethrow
grep -A5 "catch.*Exception" src/ | grep -v "throw"

# Pattern 2: Catch with only logging
grep -A3 "catch.*Exception.*{" src/ | grep "logger\."

# Pattern 3: Empty catch blocks
grep -A1 "catch.*Exception.*{" src/ | grep "^[[:space:]]*}$"
```

**Review each match for:**
- Does it log error? ✅ Good
- Does it rethrow? ✅ Required for event handlers
- Does it commit offset? ❌ Dangerous

### 3. Audit Event Publishers

Check `KafkaEventPublisher` and similar classes:

**Files to review:**
- `src/main/java/org/chucc/vcserver/event/KafkaEventPublisher.java`
- Any class with "Publisher" in name

**Verify:**
- Exceptions during publish are properly propagated
- Failed publishes don't silently succeed
- Callers are notified of publish failures

### 4. Audit Command Handlers

Command handlers publish events - verify they handle failures:

**Pattern to check:**
```java
// ❌ DANGEROUS: Silent failure
try {
  eventPublisher.publish(event);
} catch (Exception ex) {
  logger.error("Failed to publish", ex);
  // No rethrow → caller thinks command succeeded!
}

// ✅ CORRECT: Propagate failure
try {
  eventPublisher.publish(event);
} catch (Exception ex) {
  logger.error("Failed to publish", ex);
  throw new CommandException("Failed to publish event", ex);
}
```

**Files to review:**
```bash
find src -name "*CommandHandler.java" -o -name "*Handler.java"
```

### 5. Check Repository Implementations

Repositories that use Kafka for storage should handle errors:

**Files to review:**
- `CommitRepository` implementations
- `BranchRepository` implementations
- Any repository using Kafka

**Verify:**
- Read failures propagate to caller
- Write failures propagate to caller
- No silent data loss

### 6. Review Controller Layer

Controllers should propagate errors to return proper HTTP status:

**Pattern to check:**
```java
// ❌ DANGEROUS: Silent failure returns 200 OK
@PostMapping
public ResponseEntity<?> createCommit(...) {
  try {
    commandHandler.handle(command);
    return ResponseEntity.ok().build();
  } catch (Exception ex) {
    logger.error("Failed", ex);
    return ResponseEntity.ok().build(); // ❌ Wrong!
  }
}

// ✅ CORRECT: Propagate error (GlobalExceptionHandler handles it)
@PostMapping
public ResponseEntity<?> createCommit(...) {
  commandHandler.handle(command); // Throws on failure
  return ResponseEntity.ok().build();
}
```

**Files to review:**
```bash
find src -name "*Controller.java"
```

### 7. Create Checklist of Findings

Document all findings in a table:

| File | Line | Pattern | Issue | Fix Required |
|------|------|---------|-------|--------------|
| ReadModelProjector.java | 166 | catch + rethrow | ✅ Correct | No |
| ... | ... | ... | ... | ... |

### 8. Fix Any Issues Found

For each issue found:
1. Create fix in code
2. Add test to prevent regression
3. Document why fix is needed (code comment)

### 9. Add Static Analysis Rule (Optional)

Consider adding Checkstyle/PMD rule to prevent future regressions:

**PMD Rule:**
```xml
<rule ref="category/java/errorhandling.xml/AvoidCatchingGenericException">
  <properties>
    <property name="violationSuppressXPath"
              value="//ClassOrInterfaceDeclaration[@SimpleName='ReadModelProjector']"/>
  </properties>
</rule>
```

**Checkstyle Rule:**
```xml
<module name="IllegalCatch">
  <property name="illegalClassNames" value="java.lang.Exception, java.lang.Throwable"/>
  <message key="illegal.catch"
           value="Catching generic Exception can hide errors - rethrow or catch specific exceptions"/>
</module>
```

## Search Commands Reference

```bash
# 1. Find all Kafka listeners
grep -rn "@KafkaListener" src/

# 2. Find catch blocks
grep -rn "catch.*Exception" src/ | grep -v "test"

# 3. Find event publishers
find src/main -name "*Publisher*.java"

# 4. Find command handlers
find src/main -name "*Handler.java" -o -name "*Command.java"

# 5. Find controllers
find src/main -name "*Controller.java"

# 6. Find repository implementations
find src/main -name "*Repository*.java" | grep -v "interface"

# 7. Check for empty catch blocks
grep -A2 "catch.*Exception" src/ | grep -B1 "^[[:space:]]*}$"

# 8. Check for catches with only logging
grep -A5 "catch.*Exception" src/ | grep -B3 "logger\." | grep -v "throw"
```

## Acceptance Criteria

- [ ] All `@KafkaListener` methods audited for exception handling
- [ ] All event publishers verified to propagate failures
- [ ] All command handlers verified to propagate failures
- [ ] All repository implementations audited
- [ ] All controllers verified to propagate errors
- [ ] Checklist of findings documented
- [ ] All issues fixed (if any found)
- [ ] Tests added for any fixes
- [ ] Consider adding static analysis rule to prevent regressions
- [ ] Zero quality violations

## Files to Review

**Must Review:**
- `src/main/java/org/chucc/vcserver/projection/ReadModelProjector.java` ✅
- `src/main/java/org/chucc/vcserver/event/KafkaEventPublisher.java`
- All `*Controller.java` files
- All `*CommandHandler.java` files
- All `*Repository*.java` implementations

**Optional Review:**
- Service layer classes
- Utility classes with Kafka logic
- Test fixtures (ensure tests don't hide failures)

## Expected Outcome

One of:
1. **All correct:** Document that audit found no issues
2. **Issues found:** Fix all issues with tests + documentation

## Estimated Time

1-2 hours (mostly grep/review, minimal fixes expected)
