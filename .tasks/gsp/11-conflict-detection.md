# Task 11: Conflict Detection for Graph Operations

## Objective
Implement semantic overlap detection for concurrent graph writes.

## Background
When multiple clients modify the same graph concurrently, the server must detect conflicts and return 409 with detailed conflict information. This extends the existing conflict detection for SPARQL updates.

## Tasks

### 1. Review Existing Conflict Detection
- Examine `src/main/java/org/chucc/vcserver/command/PatchIntersection.java`
- Understand how conflict detection works for SPARQL patches
- Identify reusable components

### 2. Extend PatchIntersection for Graph-Level Conflicts
Update or extend `PatchIntersection`:
- Ensure it detects conflicts at quad level (g, s, p, o)
- Ensure `graph` field is populated in conflict items
- Add method: `List<ConflictItem> detectGraphConflicts(RdfPatch patch1, RdfPatch patch2, String graphIri)`

### 3. Integrate Conflict Detection in Command Handlers
Update PutGraphCommandHandler, PostGraphCommandHandler, DeleteGraphCommandHandler, PatchGraphCommandHandler:
- Before publishing event, check if branch head has advanced
- If advanced, compute intersection between our patch and patches since base
- If conflicts found, throw ConflictException (already exists?)
- Return 409 with problem+json including conflicts array

### 4. Create GraphConflictException (if needed)
Create `src/main/java/org/chucc/vcserver/exception/GraphConflictException.java`:
- Extends base exception
- Includes List<ConflictItem> conflicts
- Includes `code` field ("concurrent_write_conflict")

### 5. Update Exception Handler
Update global exception handler to return proper problem+json for GraphConflictException:
```json
{
  "type": "about:blank",
  "title": "Concurrent write conflict",
  "status": 409,
  "code": "concurrent_write_conflict",
  "conflicts": [
    {
      "graph": "http://example.org/g",
      "subject": "http://example.org/s",
      "predicate": "http://example.org/p",
      "object": "value",
      "details": "Overlapping modification"
    }
  ]
}
```

### 6. Write Unit Tests
- Test conflict detection with overlapping ADD/DELETE
- Test no conflict when operations are disjoint
- Test conflict item includes graph field

### 7. Write Integration Tests
Update integration tests:
- Test concurrent PUT operations return 409
- Test concurrent PATCH operations return 409
- Test conflict response includes graph field
- Test conflict response has correct code field

## Acceptance Criteria
- [ ] Conflict detection works at quad level (g,s,p,o)
- [ ] 409 returned with detailed conflict information
- [ ] Conflict items include required `graph` field
- [ ] All unit and integration tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 10 (PATCH operation)

## Estimated Complexity
Medium (5-6 hours)
