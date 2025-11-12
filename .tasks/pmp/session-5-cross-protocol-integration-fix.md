# Session 5: Fix Cross-Protocol Integration Test

## Issue

The `prefixesShouldPersistAfterGraphStoreOperation()` test in PrefixManagementIT is failing with a 500 Internal Server Error when combining prefix operations (PATCH) with Graph Store Protocol operations (PUT graph).

**Test Location:** `src/test/java/org/chucc/vcserver/integration/PrefixManagementIT.java:505`

**Current Status:** Test is disabled with `@Disabled` annotation and references this task (PMP-TODO).

## Problem Details

### Test Scenario
1. Add prefix via PATCH: `foaf -> http://xmlns.com/foaf/0.1/`
2. Perform GSP PUT operation: Add graph with turtle data
3. Query prefixes again - **Expected:** 200 OK, **Actual:** 500 Internal Server Error

### Error Message
```
gspResponse.getStatusCode() = 500 INTERNAL_SERVER_ERROR
Expected: 201 CREATED
```

## Investigation Required

1. **Check GSP operation logs**: Why is GSP PUT returning 500 after prefix operation?
2. **Check event processing order**: Are prefix events conflicting with GSP events?
3. **Check materialized view state**: Is the branch HEAD state corrupted?
4. **Test isolation**: Is this related to projector-disabled mode?

## Debugging Steps

1. Enable verbose logging for test:
   ```java
   @TestPropertySource(properties = {
       "logging.level.org.chucc.vcserver=DEBUG",
       "logging.level.org.apache.jena=DEBUG"
   })
   ```

2. Check CreateCommitCommandHandler for conflicts between prefix patches and GSP patches

3. Test individually:
   - Prefix operation alone (works ✓)
   - GSP operation alone (works ✓)
   - Sequential: Prefix → GSP (fails ✗)
   - Sequential: GSP → Prefix (test needed)

## Expected Behavior

Prefix changes and graph changes should be independent:
- Prefix events create commits with PA/PD directives
- GSP events create commits with A/D quad directives
- Both should work sequentially on the same branch

## Acceptance Criteria

- [ ] `prefixesShouldPersistAfterGraphStoreOperation()` test passes
- [ ] GSP PUT operation returns 201 CREATED after prefix operation
- [ ] Final prefix query returns 200 OK with correct prefixes
- [ ] No 500 errors or exceptions in logs
- [ ] Test can run in projector-disabled mode (API layer only)

## Estimated Time: 60 minutes

- 20 min: Debug 500 error root cause
- 20 min: Fix issue (likely in command handler or patch processing)
- 20 min: Add additional cross-protocol tests (GSP → Prefix order, multiple rounds)

## Related Files

- `src/test/java/org/chucc/vcserver/integration/PrefixManagementIT.java`
- `src/main/java/org/chucc/vcserver/command/UpdatePrefixesCommandHandler.java`
- `src/main/java/org/chucc/vcserver/command/CreateCommitCommandHandler.java`
- `src/main/java/org/chucc/vcserver/controller/PrefixManagementController.java`
- `src/main/java/org/chucc/vcserver/controller/GraphStoreController.java`
