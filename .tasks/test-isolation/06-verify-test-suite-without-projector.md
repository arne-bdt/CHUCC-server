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

- [x] Full test suite runs successfully
- [x] Test count: 819 tests (27 new tests added), 0 failures, 0 errors, 9 skipped
- [x] BUILD SUCCESS message appears
- [x] No "Branch not found" errors in logs
- [x] No "Cannot cherry-pick to non-existent branch" errors in logs
- [x] No "Failed to project event" errors in logs
- [x] Test execution time reasonable (50s - even faster than baseline!)
- [x] test-output.log saved for review

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

**Completed: 2025-10-09 18:57**

```
Date: 2025-10-09 18:57:18
Maven version: Apache Maven 3.9.9
Java version: 21.0.7 (Eclipse Adoptium)

Test Execution Summary:
======================
Tests run: 819
Failures: 0
Errors: 0
Skipped: 9
Total time: 50.364 s (~50 seconds)

Error Analysis:
==============
ERROR count: 3 (all expected - invalid RDF syntax tests)
  - org.apache.jena.riot errors from RDF parsing tests
  - All intentional errors from error-handling tests

WARN count: 9 (all expected - error-handling tests)
  - Unsupported Media Type tests
  - Bad Request tests
  - Not Acceptable tests
  - Unprocessable Entity tests
  - All from GraphStoreErrorHandlingIT and related tests

Cross-Test Contamination:
========================
"Branch not found" count: 0 ✅
"Cannot cherry-pick to non-existent branch" count: 0 ✅
"Failed to project event" count: 0 ✅

Conclusion:
==========
✅ All tests pass (819 tests, 0 failures, 0 errors)
✅ No cross-test contamination (zero contamination errors)
✅ Excellent execution time (50s vs ~68s baseline = 26% faster!)
✅ Clean logs (all ERROR/WARN from intentional error tests)

Overall: ✅ SUCCESS - Test isolation implementation VERIFIED

Performance Improvement:
=======================
Baseline (with projector enabled everywhere): ~68 seconds
Current (projector disabled by default): 50.364 seconds
Improvement: 17.636 seconds (26% faster)

Additional Notes:
================
- Test count increased from baseline 792 to 819 (27 new tests added)
- New tests from recent features: GraphStoreErrorHandlingIT, etc.
- Zero unexpected errors in logs
- All skipped tests (9) are intentional (@Disabled or @Ignore annotations)
- Projector-enabled tests (GraphEventProjectorIT, etc.) all passed
```
