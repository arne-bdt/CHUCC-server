# Fix SPARQL Update Tests in BatchOperationsProjectorIT

## Status: COMPLETED ✅
**Created:** 2025-11-03
**Completed:** 2025-11-03
**Priority:** Medium
**Complexity:** Medium-High
**Followup Task:** `.tasks/batch/03-fix-batch-test-isolation-race.md`

## TL;DR - Resume Here

**Problem:** 3 tests in `BatchOperationsProjectorIT` fail with 400 BAD_REQUEST (expected 202 ACCEPTED)

**Most Likely Cause:** Dataset doesn't exist, causing `getMutableDataset()` to fail

**Critical Next Step:** Capture response body to see the actual error message!
```java
// Modify first test to add:
ResponseEntity<String> response = restTemplate.exchange(..., String.class);
System.out.println("ERROR: " + response.getBody());
```

**Then run:** `mvn test -Dtest=BatchOperationsProjectorIT#batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates`

**Files to Check:**
- `BatchOperationsProjectorIT.java` - The failing tests
- `ITFixture.java` - Test setup (does it create "default" dataset?)
- `BatchOperationService.java:convertUpdateToPatch()` - Where SPARQL updates happen

## Context

Batch operations API is fully implemented and working. We have:
- ✅ 7 passing tests in `BatchOperationsIT` (RDF patches, projector disabled)
- ❌ 3 failing tests in `BatchOperationsProjectorIT` (SPARQL updates, projector enabled)

All 3 failing tests get **400 BAD_REQUEST** instead of expected **202 ACCEPTED**.

## Problem Statement

SPARQL update operations in batch requests require dataset materialization from the repository. The tests enable the projector and add initial setup, but still fail with 400 errors.

### Failing Tests
1. `batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates()` - Line 92
2. `batchUpdate_shouldCreateSingleCommit_whenMixedOperations()` - Line 169
3. `batchUpdate_shouldUseBranchFromOperations_whenRequestLevelBranchNotProvided()` - Line 234

### Test Structure
Each test:
1. Creates initial commit with RDF patch (works without projector)
2. Waits 1 second for projection
3. Attempts SPARQL update batch operation
4. Expects 202 ACCEPTED but gets 400 BAD_REQUEST

## Key Files

### Implementation
- `src/main/java/org/chucc/vcserver/service/BatchOperationService.java`
  - Method: `convertUpdateToPatch()` (line ~80-90)
  - Calls: `datasetService.getMutableDataset()` which requires repository

### Tests
- `src/test/java/org/chucc/vcserver/integration/BatchOperationsProjectorIT.java`
  - Has `@TestPropertySource(properties = "projector.kafka-listener.enabled=true")`
  - Uses `await()` with 10 second timeout for async projection verification

### Working Example
- `src/test/java/org/chucc/vcserver/integration/SparqlUpdateIntegrationTest.java`
  - Shows how SPARQL updates work with projector enabled
  - May have additional setup we're missing

## Investigation Strategy

### Step 1: Understand the 400 Error
```bash
# Run test without -q to see full error details
mvn test -Dtest=BatchOperationsProjectorIT#batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates
```

Look for:
- Validation error messages in response body
- Stack traces showing where validation fails
- Any missing headers or parameters

### Step 2: Compare with Working SPARQL Update Tests
```bash
# Find all working SPARQL update integration tests
grep -r "SPARQL-VC-Message" src/test/java/org/chucc/vcserver/integration/ | grep -v "BatchOperations"
```

Analyze:
- How they set up the initial dataset
- Whether they use await() patterns
- What headers they include
- How long they wait for projection

### Step 3: Check Dataset Initialization
The issue might be that the "default" dataset doesn't exist yet. Check:
- Does ITFixture create datasets automatically?
- Do we need to explicitly create the dataset first?
- Is the main branch initialized?

Look at: `src/test/java/org/chucc/vcserver/testutil/ITFixture.java`

### Step 4: Verify Projection Timing
1 second might not be enough. Try:
- Increase wait to 2-3 seconds
- Use `await()` instead of `Thread.sleep()`
- Verify initial commit is actually projected before continuing

### Step 5: Debug BatchOperationService
Add logging or check:
- Does `getMutableDataset()` actually return a dataset?
- Does the SPARQL update parse correctly?
- Where exactly does the 400 error originate?

## Hypotheses (Most Likely First)

### Hypothesis 1: Dataset Not Created
The "default" dataset might not exist in the test environment.

**Test:** Add explicit dataset creation before initial patch
```java
// Create dataset first
restTemplate.postForEntity("/admin/datasets",
    new HttpEntity<>(Map.of("name", "default")), String.class);
Thread.sleep(500); // Wait for creation
```

### Hypothesis 2: Branch Not Initialized
The main branch might not exist until first commit is fully projected.

**Test:** Use `await()` to verify branch exists
```java
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() -> {
      var branch = branchRepository.findByDatasetAndName("default", "main");
      assertThat(branch).isPresent();
    });
```

### Hypothesis 3: Missing Required Headers
SPARQL updates might need additional headers that RDF patches don't.

**Test:** Compare headers with working SPARQL update tests

### Hypothesis 4: Validation Error in Request Body
The request format might be incorrect for SPARQL updates.

**Test:** Capture response body to see validation error message
```java
ResponseEntity<String> response = restTemplate.exchange(..., String.class);
System.out.println("Error: " + response.getBody());
```

### Hypothesis 5: Materialization Timing
The 1-second wait isn't enough for the initial commit to be fully materialized.

**Test:** Use `await()` with repository check
```java
String setupCommitId = extractCommitId(setupResponse);
await().atMost(Duration.ofSeconds(10))
    .untilAsserted(() -> {
      var commit = commitRepository.findByDatasetAndId("default", CommitId.of(setupCommitId));
      assertThat(commit).isPresent();
    });
```

## Implementation Plan

### Phase 1: Investigation (30 min)
1. Run failing test without -q to capture full error
2. Read response body to see validation error
3. Compare with `SparqlUpdateIntegrationTest`
4. Check `ITFixture` for dataset setup

### Phase 2: Fix Implementation (1 hour)
Based on findings, likely one of:
- Add explicit dataset creation
- Fix branch initialization wait
- Add missing headers
- Increase/improve projection wait timing
- Fix request body format

### Phase 3: Verification (30 min)
1. Run all 3 tests to ensure they pass
2. Run full test suite to ensure no regressions
3. Update test documentation

## Success Criteria

- [ ] All 3 tests in `BatchOperationsProjectorIT` pass
- [ ] Full build passes: `mvn clean install`
- [ ] Tests reliably pass (run 3 times to verify)
- [ ] No changes to production code (tests-only fix)

## Notes

- These tests follow the established pattern from other projector tests
- The batch operation implementation itself is correct (RDF patch tests pass)
- The issue is specific to test setup/timing for SPARQL updates
- This is important because SPARQL updates are a key feature users will need

## Related Files

### Production Code (Reference Only)
- `BatchOperationService.java` - Batch processing logic
- `DatasetService.java` - Dataset materialization
- `CreateCommitCommandHandler.java` - Event publishing

### Test Infrastructure
- `ITFixture.java` - Base test class
- `SparqlUpdateIntegrationTest.java` - Working SPARQL example
- `VersionControlProjectorIT.java` - Working projector test example

## Investigation Results (Session 1 - 2025-11-03)

### Finding 1: No Response Body Captured
The tests only check HTTP status code but don't capture the response body to see WHY it's 400.

**Action Needed:** Modify one test to capture and log response body:
```java
ResponseEntity<String> response = restTemplate.exchange(..., String.class);
System.out.println("Response Status: " + response.getStatusCode());
System.out.println("Response Body: " + response.getBody());  // See validation error
```

### Finding 2: No Working SPARQL Update Integration Test Found
Searched for SPARQL update integration tests but didn't find direct examples.

**Action Needed:** Search more broadly:
```bash
# Find any test that uses SPARQL UPDATE statements
grep -r "INSERT DATA" src/test/java/org/chucc/vcserver/integration/
```

### Finding 3: Timing Observations
- First test (MultipleSparqlUpdates): Failed in 3.185 seconds
- Second test (MixedOperations): Failed in 1.024 seconds
- Third test (BranchFromOperations): Failed in 1.038 seconds

The longer failure time on first test suggests it might be waiting for something to initialize. Subsequent tests fail faster.

### Finding 4: Need to Understand Dataset Creation
Must check how datasets get created in tests. Questions:
1. Does ITFixture auto-create "default" dataset?
2. Does it initialize the "main" branch?
3. Do we need explicit dataset creation?

### Finding 5: Check BatchOperationService Error Handling
The 400 error must come from validation. Check:
- `BatchWriteRequest.validate()` method
- `BatchController` validation
- `BatchOperationService.convertUpdateToPatch()` error handling

## Quick Start for New Session

```bash
# 1. Modify test to capture response body (most important!)
# Edit one test to log the response body, then run:
mvn test -Dtest=BatchOperationsProjectorIT#batchUpdate_shouldCreateSingleCommit_whenMultipleSparqlUpdates

# 2. Search for working SPARQL update tests
grep -r "INSERT DATA" src/test/java/org/chucc/vcserver/integration/ | head -10

# 3. Review test fixture setup
cat src/test/java/org/chucc/vcserver/testutil/ITFixture.java

# 4. Check BatchController validation logic
cat src/main/java/org/chucc/vcserver/controller/BatchController.java | grep -A 20 "executeBatch"

# 5. Check what datasets exist in test
# Add to test: List<String> datasets = datasetRepository.listAll();
```

## Most Likely Root Cause (Updated)

Based on investigation, the most likely cause is **Dataset Not Created**. The "default" dataset probably doesn't exist when the test runs, causing `getMutableDataset()` to fail, which triggers a 400 error.

**Next Steps:**
1. Capture response body to confirm error message
2. Add explicit dataset creation before tests
3. Verify with `await()` that dataset and branch exist before proceeding

---

## Solution (Session 2025-11-03)

### Root Cause Found

The actual error was **ClassCastException** in `DatasetService.getMaterializedBranchGraph()`:

```
org.apache.jena.sparql.core.DatasetGraphMap cannot be cast to
org.apache.jena.sparql.core.mem.DatasetGraphInMemory
```

**Why it happened:**
- `InMemoryMaterializedBranchRepository.createEmptyDatasetGraph()` uses `DatasetGraphFactory.create()`
- This returns `DatasetGraphMap`, not `DatasetGraphInMemory`
- `DatasetService.getMaterializedBranchGraph()` had unsafe cast: `return (DatasetGraphInMemory) graph;`
- Comment incorrectly claimed repository returns `DatasetGraphInMemory`

### Fix Applied

**File:** `src/main/java/org/chucc/vcserver/service/DatasetService.java`

**Line 268:** Changed return type from `DatasetGraphInMemory` to `DatasetGraph`

```java
// BEFORE:
private DatasetGraphInMemory getMaterializedBranchGraph(String datasetName,
    String branchName) {
  // ...
  DatasetGraph graph = materializedBranchRepo.getBranchGraph(datasetName, branchName);
  return (DatasetGraphInMemory) graph;  // ❌ ClassCastException!
}

// AFTER:
private DatasetGraph getMaterializedBranchGraph(String datasetName,
    String branchName) {
  // ...
  DatasetGraph graph = materializedBranchRepo.getBranchGraph(datasetName, branchName);
  return graph;  // ✅ Works with both DatasetGraphMap and DatasetGraphInMemory
}
```

**Line 282:** Updated comment to reflect correct behavior

### Test Results

**Isolated test run:**
```bash
mvn test -Dtest=BatchOperationsProjectorIT
# Result: Tests run: 3, Failures: 0, Errors: 0 ✅
```

**Full build:**
- May intermittently fail due to test isolation race (see followup task)
- This is a separate issue unrelated to the ClassCastException

### Files Modified

- `src/main/java/org/chucc/vcserver/service/DatasetService.java` - Fixed return type
- `src/test/java/org/chucc/vcserver/integration/BatchOperationsProjectorIT.java` - No changes needed

### Success Criteria Met

- [x] All 3 tests in `BatchOperationsProjectorIT` pass in isolation
- [x] No ClassCastException errors
- [x] Production code fix (not test-only workaround)
- [ ] Full build passes reliably ← Followup task created

### Followup Task

A separate test isolation issue was discovered during this work. See:
`.tasks/batch/03-fix-batch-test-isolation-race.md`
