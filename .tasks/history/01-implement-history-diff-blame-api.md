# Task: Implement History, Diff, and Blame Endpoints

**Status:** Not Started
**Priority:** Medium
**Category:** Version Control Protocol
**Estimated Time:** 4-5 hours

---

## Overview

Implement the three missing History API endpoints that currently return 501:
- `GET /version/history` - List commit history with filters and pagination
- `GET /version/diff` - Get changeset between two commits
- `GET /version/blame` - Get last-writer attribution for a resource

**Related Protocol Spec:** [SPARQL 1.2 Protocol Version Control Extension §3.2](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

---

## Current State

**Controller:** [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)

**Not Implemented (returns 501):**
- ❌ `GET /version/history` (line 88)
- ❌ `GET /version/diff` (line 138)
- ❌ `GET /version/blame` (line 185)

**Note:** Diff and blame endpoints check feature flags (`vcProperties.isDiffEnabled()`, `vcProperties.isBlameEnabled()`) before returning 501.

---

## Requirements

### 1. GET /version/history - List Commit History

**Request:**
```http
GET /version/history?branch=main&limit=100&offset=0&since=2025-10-01T00:00:00Z HTTP/1.1
Accept: application/json
```

**Query Parameters:**
- `branch` (optional) - Filter by branch name
- `limit` (optional, default 100) - Max results per page
- `offset` (optional, default 0) - Pagination offset
- `since` (optional) - Filter commits after timestamp (RFC3339/ISO8601)
- `until` (optional) - Filter commits before timestamp
- `author` (optional) - Filter by author name/email

**Response:** 200 OK
```json
{
  "commits": [
    {
      "id": "01933e4a-9d4e-7000-8000-000000000003",
      "message": "Add new feature",
      "author": "Alice <alice@example.org>",
      "timestamp": "2025-10-24T15:30:00Z",
      "parents": ["01933e4a-8c3d-7000-8000-000000000002"]
    },
    {
      "id": "01933e4a-8c3d-7000-8000-000000000002",
      "message": "Fix bug",
      "author": "Bob <bob@example.org>",
      "timestamp": "2025-10-24T12:00:00Z",
      "parents": ["01933e4a-7b2c-7000-8000-000000000001"]
    }
  ],
  "pagination": {
    "total": 150,
    "limit": 100,
    "offset": 0,
    "hasMore": true
  }
}
```

**Headers:**
- `Link: <...?offset=100&limit=100>; rel="next"` (RFC 5988 pagination)

**Implementation:**
- Service: `HistoryService.listHistory(dataset, filters, pagination)`
- Query `CommitRepository` with filters
- Sort by timestamp descending (newest first)

---

### 2. GET /version/diff - Diff Two Commits

**Request:**
```http
GET /version/diff?from=01933e4a-7b2c-7000-8000-000000000001&to=01933e4a-9d4e-7000-8000-000000000003 HTTP/1.1
Accept: text/rdf-patch
```

**Response:** 200 OK
```
H id <urn:uuid:01933e4a-9d4e-7000-8000-000000000003> .
H prev <urn:uuid:01933e4a-7b2c-7000-8000-000000000001> .
TC .
PA <http://example.org/g> .
A <http://example.org/subject1> <http://example.org/pred1> "value1" .
D <http://example.org/subject2> <http://example.org/pred2> "value2" .
TC .
```

**Error Responses:**
- `404 Not Found` - Either commit not found
- `404 Not Found` - Diff endpoint disabled (feature flag)

**Implementation:**
- Service: `HistoryService.diffCommits(dataset, fromCommit, toCommit)`
- Load both commit snapshots or replay events
- Compute RDF graph diff using `GraphDiffService`
- Serialize as RDF Patch using `RdfPatchSerializer`

**Note:** This is an **EXTENSION** (not in official SPARQL 1.2 spec).

---

### 3. GET /version/blame - Last-Writer Attribution

**Request:**
```http
GET /version/blame?subject=http://example.org/Alice HTTP/1.1
Accept: application/json
```

**Query Parameters:**
- `subject` (required) - Subject IRI to get attribution for

**Response:** 200 OK
```json
{
  "subject": "http://example.org/Alice",
  "triples": [
    {
      "predicate": "http://xmlns.com/foaf/0.1/name",
      "object": "\"Alice\"",
      "lastModifiedBy": "Bob <bob@example.org>",
      "lastModifiedAt": "2025-10-24T12:00:00Z",
      "commitId": "01933e4a-8c3d-7000-8000-000000000002"
    },
    {
      "predicate": "http://xmlns.com/foaf/0.1/email",
      "object": "\"alice@example.org\"",
      "lastModifiedBy": "Alice <alice@example.org>",
      "lastModifiedAt": "2025-10-23T10:00:00Z",
      "commitId": "01933e4a-7b2c-7000-8000-000000000001"
    }
  ]
}
```

**Error Responses:**
- `404 Not Found` - Subject not found in any graph
- `404 Not Found` - Blame endpoint disabled (feature flag)

**Implementation:**
- Service: `HistoryService.blameResource(dataset, subjectIri)`
- Query all triples with subject
- For each triple, find last commit that modified it
- Return attribution info

**Note:** This is an **EXTENSION** (not in official SPARQL 1.2 spec).

---

## Implementation Steps

### Step 1: Create DTOs

**New Files:**
- `dto/CommitHistoryInfo.java`
- `dto/HistoryResponse.java`
- `dto/PaginationInfo.java`
- `dto/DiffResponse.java` (or use `String` for RDF Patch)
- `dto/BlameInfo.java`
- `dto/BlameTriple.java`

### Step 2: Implement Service

**File:** `src/main/java/org/chucc/vcserver/service/HistoryService.java`

```java
@Service
public class HistoryService {
  private final CommitRepository commitRepository;
  private final GraphDiffService graphDiffService;
  private final RdfPatchSerializer rdfPatchSerializer;

  public HistoryResponse listHistory(
      String dataset,
      HistoryFilters filters,
      Pagination pagination) {
    // Query repository with filters
    // Build pagination response
  }

  public String diffCommits(String dataset, String from, String to) {
    // Load commit snapshots
    // Compute graph diff
    // Serialize as RDF Patch
  }

  public BlameInfo blameResource(String dataset, String subject) {
    // Find all triples with subject
    // For each triple, find last modifying commit
    // Build attribution response
  }
}
```

### Step 3: Update Controller

**File:** `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

Replace the three 501 stub methods with full implementations using the service layer.

### Step 4: Write Tests

**Integration Tests:**
- `HistoryControllerIT.java` - Test all three endpoints
- Test pagination (Link headers)
- Test filters (branch, author, date range)
- Test error responses (404, 400)

**Unit Tests:**
- `HistoryServiceTest.java` - Test service logic

---

## Success Criteria

- ✅ All three endpoints implemented (no 501 responses)
- ✅ DTOs created
- ✅ Service layer implemented (read-only, no CQRS needed)
- ✅ Integration tests pass
- ✅ Pagination works with Link headers (RFC 5988)
- ✅ Filters work correctly (branch, author, date range)
- ✅ RDF Patch serialization works for diff
- ✅ Zero quality violations
- ✅ Full build passes

---

## Notes

- These are **read-only** operations (no commands/events needed)
- Diff and blame are **extensions** (not in official spec)
- Feature flags control diff/blame availability
- Pagination uses RFC 5988 Link headers

---

## References

- [SPARQL 1.2 Protocol VC Extension](../../protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [HistoryController.java](../../src/main/java/org/chucc/vcserver/controller/HistoryController.java)
- [RFC 5988 - Web Linking](https://www.rfc-editor.org/rfc/rfc5988)
