# Task 06: Verify Full Test Suite Without Projector

## Objective
Run the complete test suite to verify that all integration tests pass with ReadModelProjector disabled by default, confirming proper test isolation.

## Background
After disabling projector by default and enabling it only in GraphEventProjectorIT, we need to verify that:
1. All 792 tests still pass
2. No tests have hidden dependencies on async projection
3. Test execution completes without hangs

## Tasks

### 1. Clean Build

Start with a clean build to ensure no stale state:

```bash
mvn clean
```

### 2. Run Full Test Suite

Run all tests with quiet mode for token efficiency:

```bash
mvn -q test 2>&1 | tee test-output.log
```

**Key things to monitor:**
- Final test count: Should still be 792 tests
- Failures: Should be 0
- Errors: Should be 0
- Skipped: Should be 9 (same as before)
- Build result: Should be "BUILD SUCCESS"

### 3. Check Test Execution Time

Compare execution time to baseline:

**Before changes:**
- Total time: ~01:08 min
- Tests run: 792
- Failures: 0
- Errors: 0
- Skipped: 9

**After changes:**
- Total time: Should be similar or faster (no projector overhead)
- Tests run: Should be 792
- Failures: Should be 0
- Errors: Should be 0
- Skipped: Should be 9

### 4. Review Test Output

Search for any unexpected patterns in output:

```bash
# Search for ERROR logs (should be minimal or none)
grep "ERROR" test-output.log

# Search for WARN logs related to Kafka (should be clean)
grep "WARN.*kafka" test-output.log -i

# Search for projection failures (should be none)
grep "Failed to project event" test-output.log
```

## Expected Results

### ✅ Success Indicators

1. **Test Summary**:
   ```
   Tests run: 792, Failures: 0, Errors: 0, Skipped: 9
   BUILD SUCCESS
   ```

2. **No Projection Errors**:
   - No "Branch not found" errors
   - No "Cannot cherry-pick to non-existent branch" errors
   - No "Failed to project event" errors

3. **Clean Logs**:
   - Minimal ERROR logs (only expected errors from error-handling tests)
   - No unexpected Kafka warnings
   - No cross-test contamination messages

4. **Reasonable Execution Time**:
   - Similar to or faster than ~68 seconds (01:08 min)
   - No test hangs or timeouts

### ❌ Failure Indicators

If any of these occur, investigate before proceeding:

1. **Test Failures**:
   - Any tests fail that passed before
   - Tests using await() timeout
   - Repository assertions fail unexpectedly

2. **Error Logs**:
   - "Branch not found" errors still appear
   - Kafka connection errors
   - Unexpected IllegalArgumentException

3. **Performance Issues**:
   - Tests take significantly longer than before
   - Tests hang or timeout
   - JVM shutdown issues

## Troubleshooting

### If Tests Fail

1. **Identify which test failed**:
   ```bash
   grep "FAILURE!" test-output.log
   ```

2. **Run the failing test individually**:
   ```bash
   mvn -q test -Dtest=FailingTestName
   ```

3. **Check if test depends on projection**:
   - Does it use await()?
   - Does it assert repository state?
   - Does it need `@TestPropertySource(properties = "kafka.listener.enabled=true")`?

### If Errors Appear in Logs

1. **Check error context**:
   - Which test class was running?
   - What event caused the error?
   - Is it a cross-test contamination issue?

2. **Verify projector is disabled**:
   ```bash
   grep "kafka.listener.enabled" src/main/resources/application-it.yml
   ```
   Should show: `enabled: false`

3. **Check if GraphEventProjectorIT enables projector**:
   ```bash
   grep "@TestPropertySource" src/test/java/org/chucc/vcserver/integration/GraphEventProjectorIT.java
   ```
   Should show: `@TestPropertySource(properties = "kafka.listener.enabled=true")`

## Acceptance Criteria

- [ ] Full test suite runs successfully
- [ ] Test count: 792 tests, 0 failures, 0 errors, 9 skipped
- [ ] BUILD SUCCESS message appears
- [ ] No "Branch not found" errors in logs
- [ ] No "Cannot cherry-pick to non-existent branch" errors in logs
- [ ] No "Failed to project event" errors in logs
- [ ] Test execution time reasonable (similar to ~68 seconds)
- [ ] test-output.log saved for review

## Dependencies

- Task 01 must be completed (projector disabled by default)
- Task 04 must be completed (GraphEventProjectorIT enables projector)
- Task 05 should be completed (identified projector-dependent tests)

## Next Task

Task 07: Verify No Error Logs from Cross-Test Contamination (detailed log analysis)

## Estimated Complexity

Low (20 minutes)
- Clean build: 2 minutes
- Test execution: 1-2 minutes
- Log review: 10-15 minutes
- Documentation: 5 minutes

## Output Artifacts

Save these files for review:
- `test-output.log` - Full test output
- Summary in this file's "Results" section below

## Results

*To be filled in during task execution:*

```
Date: YYYY-MM-DD HH:MM
Maven version:
Java version:

Test Execution Summary:
======================
Tests run:
Failures:
Errors:
Skipped:
Total time:

Error Analysis:
==============
ERROR count:
WARN count:
"Branch not found" count:
"Failed to project event" count:

Conclusion:
==========
✅ / ❌ All tests pass
✅ / ❌ No cross-test contamination
✅ / ❌ Reasonable execution time
✅ / ❌ Clean logs

Overall: SUCCESS / NEEDS INVESTIGATION
```
