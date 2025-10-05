# Task 06: Implement Tag Detail and Delete Endpoints

## Priority
🟡 **MEDIUM** - Complete tag management

## Dependencies
- Task 01 (error handling)

## Protocol Reference
- Section 3.5: Tags

## Context
The protocol requires GET and DELETE operations on individual tags. Currently only list (GET /version/tags) and create (POST /version/tags) exist.

## Current State
TagController has:
- ✅ GET /version/tags (list all)
- ✅ POST /version/tags (create)
- ❌ GET /version/tags/{name} (missing)
- ❌ DELETE /version/tags/{name} (missing)

## Target State
Add GET and DELETE endpoints for individual tag operations.

## Implementation Tasks

### 1. Create Tag Detail DTO
**File**: `src/main/java/org/chucc/vcserver/dto/TagDetailResponse.java`
```java
public class TagDetailResponse {
    private String name;
    private String target;        // Commit ID (UUIDv7)
    private String message;       // Optional annotation
    private String author;        // Optional
    private String timestamp;     // When tag was created (RFC 3339)

    // constructors, getters, setters
}
```

### 2. Update TagController
**File**: `src/main/java/org/chucc/vcserver/controller/TagController.java`

Add GET endpoint:
```java
@GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(
    summary = "Get tag details",
    description = "Retrieve tag details and target commit"
)
@ApiResponse(responseCode = "200", description = "Tag details")
@ApiResponse(
    responseCode = "404",
    description = "Tag not found",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<TagDetailResponse> getTag(
    @PathVariable String name
) {
    // Implementation
}
```

Add DELETE endpoint:
```java
@DeleteMapping("/{name}")
@Operation(
    summary = "Delete tag",
    description = "Delete a tag (if server policy allows)"
)
@ApiResponse(responseCode = "204", description = "Tag deleted")
@ApiResponse(
    responseCode = "404",
    description = "Tag not found",
    content = @Content(mediaType = "application/problem+json")
)
@ApiResponse(
    responseCode = "403",
    description = "Forbidden - server policy prohibits tag deletion",
    content = @Content(mediaType = "application/problem+json")
)
public ResponseEntity<Void> deleteTag(
    @PathVariable String name
) {
    // Implementation
}
```

### 3. Add Service Methods
**File**: Create or update `src/main/java/org/chucc/vcserver/service/TagService.java`

```java
public Optional<TagDetailResponse> getTagDetails(String name) {
    // Query from repository
    // Return tag details or empty
}

public void deleteTag(String name) throws TagNotFoundException, TagDeletionForbiddenException {
    // Check if tag exists
    // Check server policy (configurable)
    // Delete tag
}
```

### 4. Add Configuration for Tag Deletion Policy
**File**: `src/main/java/org/chucc/vcserver/config/VersionControlProperties.java`

Add property:
```java
private boolean tagDeletionAllowed = true;

public boolean isTagDeletionAllowed() {
    return tagDeletionAllowed;
}

public void setTagDeletionAllowed(boolean tagDeletionAllowed) {
    this.tagDeletionAllowed = tagDeletionAllowed;
}
```

### 5. Create Custom Exceptions
**File**: `src/main/java/org/chucc/vcserver/exception/TagNotFoundException.java`
**File**: `src/main/java/org/chucc/vcserver/exception/TagDeletionForbiddenException.java`

## Response Examples

### GET /version/tags/v1.0.0
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "name": "v1.0.0",
  "target": "01936c7f-8a2e-7890-abcd-ef1234567890",
  "message": "Release version 1.0.0",
  "author": "Release Manager <release@example.org>",
  "timestamp": "2025-01-10T14:30:00Z"
}
```

### DELETE /version/tags/v1.0.0
```http
HTTP/1.1 204 No Content
```

### Tag not found
```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "code": "tag_not_found"
}
```

### Deletion forbidden by policy
```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json

{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "code": "tag_deletion_forbidden",
  "detail": "Tag deletion is disabled by server policy"
}
```

## Acceptance Criteria
- [ ] GET /version/tags/{name} returns tag details
- [ ] GET returns 404 if tag doesn't exist
- [ ] DELETE /version/tags/{name} removes tag
- [ ] DELETE returns 204 No Content on success
- [ ] DELETE returns 404 if tag doesn't exist
- [ ] DELETE returns 403 if policy forbids deletion
- [ ] Configuration property controls deletion policy
- [ ] All errors use problem+json format

## Test Requirements

### Unit Tests
1. `TagControllerTest.java` (update existing)
   - Test GET with existing tag
   - Test GET with non-existent tag → 404
   - Test DELETE with existing tag
   - Test DELETE with non-existent tag → 404
   - Test DELETE when policy forbids → 403

2. `TagServiceTest.java`
   - Test getTagDetails
   - Test deleteTag with policy check

### Integration Tests
1. `TagOperationsIntegrationTest.java`
   - Create tag via POST
   - Retrieve via GET, verify details
   - Delete via DELETE
   - Verify tag no longer exists (GET → 404)
   - Test deletion policy enforcement

## Files to Create
- `src/main/java/org/chucc/vcserver/dto/TagDetailResponse.java`
- `src/main/java/org/chucc/vcserver/service/TagService.java`
- `src/main/java/org/chucc/vcserver/exception/TagNotFoundException.java`
- `src/main/java/org/chucc/vcserver/exception/TagDeletionForbiddenException.java`
- `src/test/java/org/chucc/vcserver/service/TagServiceTest.java`
- `src/test/java/org/chucc/vcserver/integration/TagOperationsIntegrationTest.java`

## Files to Modify
- `src/main/java/org/chucc/vcserver/controller/TagController.java`
- `src/main/java/org/chucc/vcserver/config/VersionControlProperties.java`
- `src/test/java/org/chucc/vcserver/controller/TagControllerTest.java`

## Build Verification
```bash
mvn clean install
```

## Estimated Complexity
**Low-Medium** - CRUD operations with policy checks

## Notes
- Tag deletion is sensitive - some orgs forbid it in production
- Consider audit logging for tag deletions
- Tags are immutable pointers - retargeting is forbidden per spec
- If attempting to retarget (future feature), use error code `tag_retarget_forbidden`
