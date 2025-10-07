# Task 11b: Separate Precondition Validation from Conflict Detection

## Problem Analysis

After reviewing the SPARQL 1.2 Protocol Version Control Extension specification (§6) and Graph Store Protocol Version Control Extension (§F), it's clear that the protocol defines **TWO separate mechanisms**:

### 1. HTTP Precondition Validation (412 PRECONDITION FAILED)
**From Protocol §6:**
> "Servers **MAY** also use HTTP preconditions for early failure:
> - For **branch resources**, set strong `ETag` to the **current head-commit-id** of that branch
> - If request includes `If-Match: "<expectedCommitId>"` and it doesn't match current head → **`412 Precondition Failed`**"

**Purpose:** Advisory fast-fail mechanism to help clients avoid wasted work

### 2. Semantic Overlap Detection (409 CONFLICT)
**From Protocol §6:**
> "Servers perform **semantic overlap detection** for conflicting triple/quad changes. On conflict: `409 Conflict` with problem+json"
>
> "Preconditions are **advisory**; servers **MUST still** perform semantic overlap detection and may still return `409` after passing preconditions."

**Purpose:** Required conflict detection mechanism

## Current Implementation Issues

1. **PreconditionService was removed** - This was incorrect; it should have been kept
2. **Conflict detection uses If-Match as baseCommit** - This is correct but incomplete
3. **Unit tests expect 409 for ETag mismatch** - Should expect 412 first, then 409 for actual conflicts
4. **Integration tests expect 412** - This is correct and currently failing

## Correct Request Flow

```
Client Request with If-Match header
         ↓
    ┌─────────────────────────────────────┐
    │ 1. Precondition Validation          │
    │    Check If-Match vs current HEAD   │
    │    → 412 if mismatch                │
    └─────────────────────────────────────┘
         ↓ (If-Match matches HEAD)
    ┌─────────────────────────────────────┐
    │ 2. Use If-Match as baseCommit       │
    │    (or current HEAD if no If-Match) │
    └─────────────────────────────────────┘
         ↓
    ┌─────────────────────────────────────┐
    │ 3. Conflict Detection               │
    │    Analyze patch intersection       │
    │    → 409 if semantic conflicts      │
    └─────────────────────────────────────┘
         ↓ (No conflicts)
    ┌─────────────────────────────────────┐
    │ 4. Create Commit                    │
    │    → 201 Created                    │
    └─────────────────────────────────────┘
```

## Implementation Plan

### Phase 1: Restore Precondition Validation (Task 11b-1)

**Files to modify:**
1. Restore `PreconditionService` with proper implementation
2. Re-add precondition checks to command handlers (BEFORE conflict detection)
3. Update `GraphStoreController` to keep both mechanisms

**Key points:**
- Precondition check returns early with 412 if If-Match doesn't match current HEAD
- If precondition passes (or no If-Match provided), continue to conflict detection
- Conflict detection uses If-Match value (or current HEAD) as baseCommit

### Phase 2: Fix Unit Tests (Task 11b-2)

**Test categories to fix:**

1. **Precondition tests (expect 412):**
   - `handle_shouldThrowPreconditionFailed_whenETagMismatch` in all 4 command handler tests
   - These should test: If-Match doesn't match current HEAD → 412

2. **Conflict detection tests (expect 409):**
   - Create NEW tests: `handle_shouldThrowConflict_whenConcurrentWrites`
   - These should test: If-Match matches HEAD, but semantic conflicts exist → 409

3. **Happy path tests:**
   - Existing tests where If-Match matches HEAD and no conflicts → 201/200

**Files to fix:**
- `PutGraphCommandHandlerTest.java`
- `PostGraphCommandHandlerTest.java`
- `PatchGraphCommandHandlerTest.java`
- `DeleteGraphCommandHandlerTest.java`

### Phase 3: Fix Integration Tests (Task 11b-3)

**Files to modify:**
1. `GraphStorePutIntegrationTest.java` - Line 213 expects 412
2. `GraphStorePostIntegrationTest.java` - Line 220 expects 412
3. `GraphStoreDeleteIntegrationTest.java` - Line 155 expects 412

**Current issue:** These tests send wrong If-Match values and expect 412, which is correct behavior.

### Phase 4: Enable and Fix Concurrent Operations Tests (Task 11b-4)

**File:** `ConcurrentGraphOperationsIntegrationTest.java`

**Current status:** Disabled due to 400 BAD_REQUEST errors

**Investigation needed:**
- Why are requests returning 400 instead of 200/201?
- Likely issue: Missing required headers or invalid request format
- Fix and re-enable tests

**Expected behavior after fix:**
- Step 1: Create graph → 200 OK, ETag returned
- Step 2: Update with matching ETag → 200 OK, new ETag
- Step 3: Update with old ETag → 409 CONFLICT (semantic overlap detected)

### Phase 5: Verification (Task 11b-5)

1. Run Phase 1 static analysis (checkstyle, spotbugs, pmd)
2. Run all unit tests → 0 failures
3. Run all integration tests → 0 failures
4. Verify both mechanisms work:
   - Send If-Match that doesn't match HEAD → 412
   - Send If-Match that matches HEAD but has conflicts → 409
   - Send If-Match that matches HEAD with no conflicts → 200/201

## Success Criteria

- ✅ All 779 tests pass (currently 772 pass, 7 fail)
- ✅ Zero Checkstyle, SpotBugs, PMD violations
- ✅ Both precondition validation (412) and conflict detection (409) work correctly
- ✅ Concurrent operations integration tests enabled and passing
- ✅ Clear separation between the two mechanisms in code and tests

## Estimated Effort

- Phase 1: 30-45 minutes
- Phase 2: 45-60 minutes
- Phase 3: 15-30 minutes
- Phase 4: 30-45 minutes (depends on root cause)
- Phase 5: 15-30 minutes

**Total: 2.5-3.5 hours**

## Notes

- The key mistake was conflating two separate concerns
- HTTP preconditions (If-Match/ETag) are a general HTTP feature (RFC 7232)
- Semantic conflict detection is a domain-specific version control feature
- Both serve different purposes and should coexist
- This aligns with the protocol specification's explicit requirement
