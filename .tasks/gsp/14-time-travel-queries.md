# Task 14: Time-Travel Graph Queries (asOf Selector)

## Objective
Ensure `asOf` selector works correctly for graph operations with inclusive timestamp semantics.

## Background
`GET /data?graph={iri}&branch=main&asOf=2025-10-01T12:00:00Z` retrieves graph state as of the specified timestamp (inclusive). This selector is already implemented for SPARQL queries; we need to ensure it works for GSP.

## Tasks

### 1. Review Existing asOf Implementation
- Read `src/main/java/org/chucc/vcserver/service/TimestampResolutionService.java`
- Understand how asOf resolution works
- Verify it's already integrated with SelectorResolutionService

### 2. Test asOf with Graph Operations
Verify that asOf selector is properly wired through:
- GraphStoreController validates asOf parameter
- SelectorResolutionService resolves asOf to CommitId
- GET/HEAD return graph state at resolved commit

### 3. Write Integration Tests
Create or update `src/test/java/org/chucc/vcserver/integration/GraphStoreTimeTraveIT.java`:
- Test GET graph with asOf selector
- Test asOf is inclusive (commit at exact timestamp is included)
- Test asOf with branch selector (valid combination)
- Test asOf without branch selector (invalid, should return 400)
- Test asOf resolves to correct commit in history
- Test asOf with future timestamp (returns latest commit)

### 4. Document asOf Behavior
Update OpenAPI documentation in GraphStoreController:
- Document asOf parameter on all read operations
- Clarify inclusive semantics
- Provide examples

## Acceptance Criteria
- [ ] asOf selector works for GET/HEAD operations
- [ ] asOf is inclusive (commit at timestamp is selected)
- [ ] asOf validation works (requires branch)
- [ ] All integration tests pass
- [ ] Documentation updated
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 13 (batch operations)

## Estimated Complexity
Low-Medium (3-4 hours)
