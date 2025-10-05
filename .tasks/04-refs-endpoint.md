# Task 04: Implement GET /version/refs Endpoint

## Priority
🟡 **MEDIUM** - Core discovery endpoint

## Dependencies
- Task 01 (error handling)

## Protocol Reference
- Section 3.2: Refs and Commits

## Context
Clients need a way to list all branches and tags with their target commits. This is a core discovery endpoint that provides the current state of all refs.

## Current State
- Separate endpoints: GET /version/branches, GET /version/tags
- No unified refs endpoint

## Target State
- GET /version/refs returns both branches and tags with their target commits
- Response format shows ref type, name, and target commit ID

## Implementation Tasks

### 1. Create Response DTO
**File**: `src/main/java/org/chucc/vcserver/dto/RefResponse.java`
```java
public class RefResponse {
    private String type;      // "branch" or "tag"
    private String name;
    private String targetCommit; // UUIDv7
    private String message;   // Optional, for annotated tags

    // constructors, getters, setters
}
```

**File**: `src/main/java/org/chucc/vcserver/dto/RefsListResponse.java`
```java
public class RefsListResponse {
    private List<RefResponse> refs;

    // constructors, getters, setters
}
```

### 2. Create Controller Endpoint
**File**: `src/main/java/org/chucc/vcserver/controller/RefsController.java`
```java
@RestController
@RequestMapping("/version")
public class RefsController {

    @GetMapping(value = "/refs", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List all refs",
               description = "List all branches and tags with their target commits")
    public ResponseEntity<RefsListResponse> listRefs() {
        // Implementation
    }
}
```

### 3. Add Service Method
**File**: Update or create service to fetch all refs
```java
public class RefService {
    public List<RefResponse> getAllRefs() {
        // Query branches from BranchRepository
        // Query tags from TagRepository
        // Combine and return
    }
}
```

### 4. Update Existing Controllers
Keep GET /version/branches and GET /version/tags for compatibility, but they now delegate to the same underlying service.

## Response Format
```json
{
  "refs": [
    {
      "type": "branch",
      "name": "main",
      "targetCommit": "01936c7f-8a2e-7890-abcd-ef1234567890"
    },
    {
      "type": "branch",
      "name": "develop",
      "targetCommit": "01936c80-1234-7890-abcd-ef1234567890"
    },
    {
      "type": "tag",
      "name": "v1.0.0",
      "targetCommit": "01936c7f-8a2e-7890-abcd-ef1234567890",
      "message": "Release version 1.0.0"
    }
  ]
}
```

## Acceptance Criteria
- [ ] GET /version/refs returns 200 OK
- [ ] Response includes all branches
- [ ] Response includes all tags
- [ ] Each ref shows type, name, targetCommit
- [ ] Annotated tags include message field
- [ ] Content-Type is application/json
- [ ] Empty repository returns empty refs array

## Test Requirements

### Unit Tests
1. `RefsControllerTest.java`
   - Test with no refs → empty array
   - Test with only branches
   - Test with only tags
   - Test with mixed branches and tags
   - Test with annotated tags

2. `RefServiceTest.java`
   - Test ref aggregation logic
   - Test sorting (branches first, then tags, alphabetical within each)

### Integration Tests
1. `RefsEndpointIntegrationTest.java`
   - Create branches via BranchRepository
   - Create tags via TagRepository
   - GET /version/refs
   - Verify all refs returned
   - Verify correct structure

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/RefResponse.java`
- `src/main/java/org/chucc/vcserver/dto/RefsListResponse.java`
- `src/main/java/org/chucc/vcserver/controller/RefsController.java`
- `src/main/java/org/chucc/vcserver/service/RefService.java`
- `src/test/java/org/chucc/vcserver/controller/RefsControllerTest.java`
- `src/test/java/org/chucc/vcserver/service/RefServiceTest.java`
- `src/test/java/org/chucc/vcserver/integration/RefsEndpointIntegrationTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low-Medium** - Straightforward aggregation of existing data

## Notes
- This provides a single endpoint for ref discovery
- Useful for clients to get complete repository state
- Consider adding pagination if repos might have many refs
- Consider adding filter query param (e.g., ?type=branch)
