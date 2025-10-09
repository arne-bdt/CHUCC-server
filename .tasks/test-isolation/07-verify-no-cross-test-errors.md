# Task 07: Verify No Error Logs from Cross-Test Contamination

## Objective
Perform detailed analysis of test logs to confirm complete elimination of cross-test event contamination errors.

## Background
Before implementing projector isolation, we saw these ERROR logs:
- "Branch not found: feature in dataset: test-dataset"
- "Cannot cherry-pick to non-existent branch: branch-b"
- "Cannot revert to non-existent branch: main"

These errors occurred when ReadModelProjector in test class A processed events created by test class B, trying to update branches that don't exist in A's repository state.

This task verifies these errors are completely gone.

## Tasks

### 1. Run Test Suite with Full Logging

If not already done in Task 06, run tests and capture output:

```bash
mvn clean test 2>&1 | tee test-isolation-verification.log
```

### 2. Search for Specific Error Patterns

Check for each known cross-contamination error:

```bash
# Check for branch not found errors
grep -i "branch not found" test-isolation-verification.log
echo "Branch not found count: $(grep -ci 'branch not found' test-isolation-verification.log)"

# Check for cherry-pick errors
grep -i "cannot cherry-pick" test-isolation-verification.log
echo "Cherry-pick error count: $(grep -ci 'cannot cherry-pick' test-isolation-verification.log)"

# Check for revert errors
grep -i "cannot revert" test-isolation-verification.log
echo "Revert error count: $(grep -ci 'cannot revert' test-isolation-verification.log)"

# Check for any ReadModelProjector errors
grep "ReadModelProjector.*ERROR" test-isolation-verification.log
echo "Projector error count: $(grep -c 'ReadModelProjector.*ERROR' test-isolation-verification.log)"

# Check for failed projection attempts
grep "Failed to project event" test-isolation-verification.log
echo "Failed projection count: $(grep -c 'Failed to project event' test-isolation-verification.log)"
```

### 3. Analyze ERROR Logs by Context

If any ERROR logs appear, analyze them:

```bash
# Extract all ERROR lines with context
grep -B 2 -A 2 "ERROR" test-isolation-verification.log > errors-with-context.txt
```

For each ERROR:
- **Source**: Which class/method logged it?
- **Context**: Which test was running?
- **Type**: Cross-test contamination or legitimate error?
- **Action**: None needed (expected error) or fix required (unexpected error)

### 4. Compare Before/After

**Before (with projector enabled everywhere):**
- Multiple "Branch not found" errors
- Multiple "Cannot cherry-pick to non-existent branch" errors
- Multiple "Failed to project event: CherryPickedEvent" errors
- Errors repeated due to Kafka retry mechanism

**After (with projector disabled by default):**
- Should have ZERO cross-contamination errors
- GraphEventProjectorIT runs with projector enabled, processes only its own events
- Other tests run without projector, no event processing

## Expected Results

### ✅ Success (Complete Isolation)

```
Search Results:
===============
Branch not found count: 0
Cherry-pick error count: 0
Revert error count: 0
Projector error count: 0
Failed projection count: 0

ERROR log analysis:
==================
Total ERROR lines: [only legitimate errors from error-handling tests]
Cross-contamination errors: 0
Expected test errors: [count from tests like ErrorResponseIntegrationTest]

Conclusion: ✅ COMPLETE ISOLATION ACHIEVED
```

### ⚠️ Partial Success (Some Errors Remain)

If errors still appear:

1. **Check if errors are from GraphEventProjectorIT**:
   - If yes: Investigate why projector enabled test has errors
   - If no: Investigate why projector is running when it shouldn't be

2. **Check error timestamps**:
   - Do they cluster around specific test classes?
   - Do they occur during VcServerApplicationTests?

3. **Check consumer group IDs in logs**:
   - Are multiple consumers reading from same topics?
   - Are unique consumer groups working correctly?

## Verification Matrix

| Error Type | Before Fix | After Fix | Status |
|------------|-----------|-----------|--------|
| Branch not found | Many | 0 | ✅ |
| Cannot cherry-pick | Many | 0 | ✅ |
| Cannot revert | Some | 0 | ✅ |
| Failed to project event | Many | 0 | ✅ |
| Cross-test contamination | Yes | No | ✅ |

*To be filled in during task execution*

## Acceptance Criteria

- [ ] test-isolation-verification.log captured
- [ ] Zero "Branch not found" errors from cross-contamination
- [ ] Zero "Cannot cherry-pick" errors from cross-contamination
- [ ] Zero "Cannot revert" errors from cross-contamination
- [ ] Zero "Failed to project event" errors from cross-contamination
- [ ] Verification matrix completed with actual counts
- [ ] Any remaining ERRORs analyzed and categorized as expected/unexpected

## Dependencies

- Task 06 must be completed (full test suite run)

## Next Task

Task 08: Review GraphEventProjectorIT Coverage (identify missing event handler tests)

## Estimated Complexity

Low (15-20 minutes)
- Run log searches: 5 minutes
- Analyze results: 5-10 minutes
- Document findings: 5 minutes

## Findings

*To be filled in during task execution:*

```
Execution Date: YYYY-MM-DD HH:MM

Error Counts:
=============
Branch not found:
Cannot cherry-pick:
Cannot revert:
Projector errors:
Failed projections:

Analysis:
========
[Detailed analysis of any errors found]

Conclusion:
==========
✅ Complete isolation achieved
OR
⚠️ Partial success - [describe remaining issues]
OR
❌ Isolation failed - [describe problems]

Recommendation:
==============
[Proceed to next task OR fix identified issues]
```
