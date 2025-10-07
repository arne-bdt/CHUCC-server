# Task 19: Error Handling Polish and Consistency

## Objective
Ensure all error responses are consistent, complete, and follow problem+json specification.

## Background
All errors should return RFC 7807 problem+json with consistent format. Error codes should be canonical and well-documented.

## Tasks

### 1. Review All Error Responses
Audit all GraphStoreController methods:
- 400 Bad Request
- 404 Not Found
- 406 Not Acceptable
- 409 Conflict
- 412 Precondition Failed
- 415 Unsupported Media Type
- 422 Unprocessable Entity
- 500 Internal Server Error

### 2. Standardize Error Codes
Ensure canonical codes are used consistently:
- `selector_conflict` - multiple selectors provided
- `graph_not_found` - graph doesn't exist
- `concurrent_write_conflict` - concurrent modification
- `precondition_failed` - If-Match mismatch
- `invalid_patch_syntax` - malformed RDF Patch
- `patch_not_applicable` - semantic patch error

### 3. Add Helpful Error Details
Enhance error messages:
- Include specific validation failure reasons
- Provide hints for fixing errors
- Reference spec sections when applicable
- Include request ID for debugging

### 4. Create Error Response Builder
Create utility for consistent error responses:
`src/main/java/org/chucc/vcserver/util/ErrorResponseBuilder.java`:
- Method: `buildProblemDetail(HttpStatus, String code, String detail, Map<String, Object> extras)`
- Ensures consistent structure

### 5. Update Exception Handlers
Review and update global exception handlers:
- Map all GSP exceptions to correct HTTP status
- Ensure problem+json format
- Include stack trace only in debug mode

### 6. Write Error Handling Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStoreErrorHandlingIT.java`:
- Test all error codes
- Verify problem+json structure
- Verify helpful error messages
- Verify no sensitive info leaked

### 7. Document Error Codes
Create error code reference documentation:
- List all codes
- Example scenarios
- How to resolve each error

## Acceptance Criteria
- [ ] All errors return consistent problem+json
- [ ] Canonical error codes used consistently
- [ ] Error messages are helpful and specific
- [ ] All error handling tests pass
- [ ] Error documentation complete
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 18 (security validation)

## Estimated Complexity
Low-Medium (3-4 hours)
