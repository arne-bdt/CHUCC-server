# Task: Review and Enhance Superficial Tests

## Objective
Systematically identify and enhance integration tests that may be superficial - tests that appear to verify behavior but actually only test API contracts without validating the actual business logic.

## Background
During rebase implementation, we discovered that integration tests can pass while mocking away or not verifying the actual core behavior. A test that only checks HTTP status codes and response format is not sufficient - it must verify the actual state changes and business logic.

## Red Flags for Superficial Tests

### 1. **Tests that only check API responses**
- Only verifies HTTP status codes (200, 404, etc.)
- Only checks response JSON structure
- Doesn't verify repository/database state after operation
- Example: "Test passes as long as endpoint returns 200 and JSON has the right fields"

### 2. **Tests with no state verification**
- Makes HTTP request
- Checks response
- Never queries repositories to verify changes actually happened
- Example: POST creates a commit, but test doesn't verify commit exists in CommitRepository

### 3. **Tests that verify counts instead of correctness**
- Checks "2 commits were created" but not that they have correct parents
- Verifies "branch updated" but not that it points to correct commit
- Example: Rebase test that checks `rebasedCount == 2` but doesn't verify commit graph structure

### 4. **Integration tests with majority validation tests**
- 5 out of 7 tests are "missing parameter" tests
- Only 1-2 tests actually test the happy path business logic
- Validation tests are important but shouldn't dominate

## Methodology

### Phase 1: Identify Candidates (Automated)
```bash
# Find integration tests
find src/test/java -name "*IntegrationTest.java" -type f

# For each test, check for warning signs:
# - High ratio of 400/404 tests to 200 tests
# - No repository queries after operations
# - Tests that don't assert on domain objects
```

### Phase 2: Manual Review Checklist

For each integration test file, ask:

1. **Does it test actual state changes?**
   - [ ] Queries repository after operation
   - [ ] Verifies domain object properties (not just IDs)
   - [ ] Checks relationships between entities

2. **Does it test business logic correctness?**
   - [ ] Verifies algorithm results (e.g., commit parents, graph structure)
   - [ ] Checks data was preserved/transformed correctly
   - [ ] Validates invariants (e.g., "old commits still exist after rebase")

3. **Test coverage balance**
   - [ ] At least 50% of tests verify happy path behavior
   - [ ] Not dominated by validation/error tests
   - [ ] Core functionality tested, not just edge cases

### Phase 3: Enhancement Pattern

When enhancing a test, add assertions that verify:

```java
// BEFORE (superficial)
@Test
void operation_shouldReturn200_whenSuccessful() {
    ResponseEntity<String> response = restTemplate.exchange(...);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    JsonNode json = objectMapper.readTree(response.getBody());
    assertThat(json.get("id").asText()).isNotNull();
}

// AFTER (verifies behavior)
@Test
void operation_shouldReturn200_whenSuccessful() {
    ResponseEntity<String> response = restTemplate.exchange(...);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    JsonNode json = objectMapper.readTree(response.getBody());
    String id = json.get("id").asText();

    // ✅ Verify actual state changes
    Entity entity = repository.findById(id).orElseThrow();
    assertThat(entity.getProperty()).isEqualTo(expectedValue);

    // ✅ Verify relationships
    assertThat(entity.getParent().getId()).isEqualTo(parentId);

    // ✅ Verify business logic invariants
    assertThat(entity.isValid()).isTrue();
    assertThat(relatedEntity.wasUpdated()).isTrue();
}
```

## Priority Review List

Based on patterns, review these test files first:

### High Priority
1. **CherryPickIntegrationTest** - Similar to rebase, verify commit graph structure
2. **ResetBranchCommandHandlerTest** - Verify actual branch pointer changes
3. **RevertCommitCommandHandlerTest** - Verify inverse patch applied correctly

### Medium Priority
4. **CommitOperationsIntegrationTest** - Verify patches stored and retrievable
5. **BranchOperationsIntegrationTest** - Verify branch references correct
6. **TagOperationsIntegrationTest** - Verify tag immutability and correctness

### Lower Priority (likely OK, but verify)
7. **SnapshotServiceIT** - Already runs full Kafka, likely comprehensive
8. **EventPublisherKafkaIT** - Event publishing verification
9. **ReadModelProjectorIT** - Projection verification

## Deliverables

For each enhanced test file:
1. Document what was superficial (in commit message)
2. List new assertions added
3. Confirm all new assertions pass
4. Run full test suite to ensure no regressions

## Success Criteria

- [ ] All integration tests verify actual business logic, not just API contracts
- [ ] Each integration test has repository/state verification after operations
- [ ] Test suite would catch if business logic was mocked away
- [ ] Future developers can't accidentally write superficial tests (CLAUDE.md updated)

## Notes

- This is a quality improvement task, not a bug fix
- Tests may all be passing - that's the problem!
- Focus on asking: "Would this test pass if I commented out the business logic?"
- If answer is "yes", the test is superficial and needs enhancement
