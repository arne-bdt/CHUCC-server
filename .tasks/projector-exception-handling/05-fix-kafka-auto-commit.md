# Task 05: Fix Kafka Auto-Commit Configuration (IMPROVEMENT)

## Objective

Improve Kafka consumer configuration to ensure offset is ONLY committed on successful event processing.

## Issue Summary

**Current Configuration (KafkaConfig.java:197):**
```java
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
```

**Problem:**
- Auto-commit happens at fixed intervals (default: 1 second)
- If event processing fails AFTER auto-commit interval → offset MAY be committed
- Creates a race condition where failed events might be skipped

**Example Race Condition:**
```
Time 0ms:    Receive event A
Time 100ms:  Start processing event A
Time 1000ms: Auto-commit runs → offset advanced
Time 1100ms: Event A processing fails → exception thrown
Result:      Event A LOST (offset already committed!)
```

## Recommended Fix

### Step 1: Disable Auto-Commit

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`
**Line:** 197

**Change:**
```java
// Before
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

// After
configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // Manual commit only
```

### Step 2: Set ACK Mode to RECORD

**File:** `src/main/java/org/chucc/vcserver/config/KafkaConfig.java`
**Method:** `kafkaListenerContainerFactory()`
**Line:** ~206

**Change:**
```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent>
    kafkaListenerContainerFactory() {
  ConcurrentKafkaListenerContainerFactory<String, VersionControlEvent> factory =
      new ConcurrentKafkaListenerContainerFactory<>();
  factory.setConsumerFactory(consumerFactory());
  factory.setConcurrency(1); // Single consumer for ordered processing

  // CRITICAL: Only commit offset after successful event processing
  factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

  return factory;
}
```

**Import needed:**
```java
import org.springframework.kafka.listener.ContainerProperties;
```

### Step 3: Verify Behavior

With these changes:
- ✅ Offset commits ONLY after `handleEvent()` returns successfully
- ✅ If `handleEvent()` throws exception → offset NOT committed
- ✅ Kafka retries failed event
- ✅ No race condition

## Testing

### Test 1: Verify Manual Commit

```java
@Test
void eventProcessingFailure_shouldNotCommitOffset() {
  // 1. Get current offset
  long offsetBefore = getCurrentOffset("vc.test.events", "read-model-projector");

  // 2. Publish event that will fail processing
  publishEventThatWillFail();

  // 3. Wait for processing attempt
  await().atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> {
        // Verify error was logged
        assertThat(logCaptor.getErrorLogs())
            .anyMatch(log -> log.contains("Failed to project event"));
      });

  // 4. Verify offset NOT advanced
  long offsetAfter = getCurrentOffset("vc.test.events", "read-model-projector");
  assertThat(offsetAfter).isEqualTo(offsetBefore);
}
```

### Test 2: Verify Successful Commit

```java
@Test
void eventProcessingSuccess_shouldCommitOffset() {
  // 1. Get current offset
  long offsetBefore = getCurrentOffset("vc.test.events", "read-model-projector");

  // 2. Publish valid event
  publishValidEvent();

  // 3. Wait for successful processing
  await().atMost(Duration.ofSeconds(5))
      .untilAsserted(() -> {
        // Verify event was processed (check repository)
        assertThat(commitRepository.findById(commitId)).isPresent();
      });

  // 4. Verify offset advanced
  long offsetAfter = getCurrentOffset("vc.test.events", "read-model-projector");
  assertThat(offsetAfter).isGreaterThan(offsetBefore);
}
```

## Priority

**MEDIUM** - Current implementation works MOST of the time (race condition is rare), but improvement eliminates the race condition entirely.

**Note:** This is less critical than Task 04 (command handler fire-and-forget) because:
- Auto-commit race condition is rare (events usually process in <1 second)
- Deduplication provides additional safety (retried events skipped)
- Task 04 affects 100% of writes (fire-and-forget always wrong)

## Acceptance Criteria

- [ ] `ENABLE_AUTO_COMMIT_CONFIG` set to `false`
- [ ] `AckMode.RECORD` configured in listener factory
- [ ] Tests verify offset NOT committed on failure
- [ ] Tests verify offset IS committed on success
- [ ] Zero quality violations
- [ ] All tests pass

## Estimated Time

1 hour (simple config change + tests)

## References

- [Spring Kafka ACK Modes](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html#committing-offsets)
- [Kafka Consumer Manual Commit](https://kafka.apache.org/documentation/#consumerconfigs_enable.auto.commit)
- [ADR-0003](../../docs/architecture/decisions/0003-projector-fail-fast-exception-handling.md)
