# Task 15: Cross-Protocol Interoperability Testing

## Objective
Verify that SPARQL Protocol and Graph Store Protocol extensions work together seamlessly with shared commit history.

## Background
Both protocols share the same commit DAG, branches, tags, and /version/ endpoints. Operations from one protocol should be visible and queryable from the other.

## Tasks

### 1. Write Cross-Protocol Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/CrossProtocolInteroperabilityIT.java`:

Test scenarios:
- **GSP → Protocol**:
  - Create commit via GSP PUT
  - Verify commit appears in `GET /version/history`
  - Verify commit metadata in `GET /version/commits/{id}`
  - Query graph via SPARQL Protocol with commit selector

- **Protocol → GSP**:
  - Create commit via SPARQL UPDATE (when implemented)
  - Retrieve graph via GSP GET with commit selector
  - Verify graph reflects SPARQL update

- **Shared branches**:
  - Create branch via Protocol
  - Commit via GSP to that branch
  - Verify branch head updated
  - Query via Protocol with branch selector

- **Shared tags**:
  - Create commits via GSP
  - Tag commit via `POST /version/tags`
  - Retrieve graph via GSP with commit selector (tag target)

- **Shared merge**:
  - Create two branches
  - Commit to each via GSP
  - Merge via `POST /version/merge`
  - Verify merged graph via GSP GET

### 2. Test Conflict Item Schema Consistency
Verify that conflict items from both protocols have consistent schema:
- `graph` field present and required in all conflict items
- `subject`, `predicate`, `object` fields present
- Same JSON schema for conflicts

### 3. Test Selector Consistency
Verify selectors work identically:
- Same validation rules (mutual exclusion)
- Same resolution logic (branch, commit, asOf)
- Same error codes (selector_conflict)

### 4. Test Batch Endpoint Separation
Verify that:
- `/version/batch` (Protocol) and `/version/batch-graphs` (GSP) are distinct
- Each endpoint handles its own operations
- No routing ambiguity

### 5. Verify Shared /version/ Namespace
Test that all /version/ endpoints work for both protocols:
- `GET /version/refs` shows commits from both protocols
- `GET /version/history` includes GSP and Protocol commits
- `GET /version/commits/{id}` works for all commit types
- `POST /version/merge` works with commits from both protocols

## Acceptance Criteria
- [ ] Commits from GSP visible via Protocol endpoints
- [ ] Commits from Protocol visible via GSP endpoints
- [ ] Branches and tags are shared
- [ ] Selectors work consistently across protocols
- [ ] Conflict schema is consistent
- [ ] Batch endpoints are distinct
- [ ] All cross-protocol tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 14 (time-travel queries)
- Full implementation of GSP operations

## Estimated Complexity
Medium (5-6 hours)
