# Task 18: Security Validation and Input Sanitization

## Objective
Ensure all GSP endpoints properly validate and sanitize inputs to prevent security vulnerabilities.

## Background
Security considerations from spec §L:
- Graph IRI validation/authorization
- Author identity alignment with authentication
- Commit message sanitization
- Rate limiting for large requests

## Tasks

### 1. Graph IRI Validation
Review and enhance GraphParameterValidator:
- Validate IRI syntax strictly
- Prevent injection attacks via graph parameter
- Consider whitelist/blacklist of allowed graph IRIs
- Add max length limits

### 2. Content Size Limits
Add request body size limits:
- Configure max request size in Spring Boot (server.tomcat.max-http-post-size)
- Return 413 Payload Too Large for oversized requests
- Add configurable limits per operation type

### 3. Author Identity Validation
Create AuthorValidationService:
- Align author header with authenticated user
- Reject mismatched authors
- Sanitize author field (prevent XSS in logs/UI)

### 4. Commit Message Sanitization
Create MessageSanitizationService:
- Strip HTML/script tags from commit messages
- Limit message length
- Prevent injection in logs

### 5. Rate Limiting (Optional)
Consider adding rate limiting:
- Limit operations per user per time window
- Return 429 Too Many Requests when exceeded
- Use Spring Boot rate limiting or custom solution

### 6. Security Integration Tests
Create `src/test/java/org/chucc/vcserver/integration/GraphStoreSecurityIT.java`:
- Test invalid graph IRI rejected
- Test oversized request rejected (413)
- Test author validation
- Test commit message sanitization
- Test malicious input handling

### 7. Security Audit
- Review all input validation
- Check for injection vulnerabilities
- Verify proper error handling (don't leak sensitive info)
- Update security documentation

## Acceptance Criteria
- [ ] All inputs validated and sanitized
- [ ] Size limits enforced
- [ ] Author identity validated
- [ ] Security tests pass
- [ ] Security documentation updated
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 17 (performance optimization)

## Estimated Complexity
Medium (4-5 hours)
