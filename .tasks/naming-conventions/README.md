# Naming Conventions - Optional Code Enhancements

This directory contains optional enhancement tasks for naming convention validation. These tasks extend the basic validation that's already implemented in the codebase.

---

## Current Status

✅ **Core validation is COMPLETE** - No immediate action needed!

Current implementation already enforces:
- Pattern: `^[A-Za-z0-9._-]+$` (correct!)
- Unicode NFC normalization (correct!)
- Max dataset length: 249 characters (correct!)
- Protocol specifications updated (complete!)

---

## Optional Enhancement Tasks

These tasks add **defensive depth** and **user-friendly validation**:

### 1. Reserved Names Tests
**File:** `01-reserved-names-tests.md`
**Priority:** Medium
**Time:** 1-2 hours

Add comprehensive tests for edge cases:
- `.` and `..` (reserved)
- Names starting with `_` (internal use)
- Names starting/ending with `.` (hidden files)

**Why:** Improve test coverage, document expected behavior

---

### 2. Windows Reserved Names
**File:** `02-windows-reserved-names.md`
**Priority:** Low
**Time:** 2-3 hours

Reject Windows device names (`CON`, `PRN`, `AUX`, `COM1-9`, `LPT1-9`).

**Why:** Prevent cross-platform issues, future-proof for file-based features

**Note:** Create shared `IdentifierValidator` utility for reuse

---

### 3. Max Length Enforcement
**File:** `03-max-length-enforcement.md`
**Priority:** Medium
**Time:** 1 hour

Add 255-character limit for branches and tags (already spec'd in protocol).

**Why:** Prevent URL length issues, align with protocol specification

**Note:** Dataset max length (249) is already enforced ✅

---

### 4. OpenAPI Documentation
**File:** `04-openapi-documentation.md`
**Priority:** High
**Time:** 2-3 hours

Update OpenAPI specs with identifier format details, examples, and validation rules.

**Why:** Help API consumers understand constraints, generate better client libraries

---

## Recommended Order

If implementing these tasks, follow this order:

1. **Task 03** (Max Length) - Quick, low-risk, aligns with protocol
2. **Task 01** (Reserved Names Tests) - Improves test coverage
3. **Task 02** (Windows Names) - Refactor to shared validator
4. **Task 04** (OpenAPI Docs) - Document all validation rules

---

## Task Dependencies

```
Task 01 (Tests) ────┐
                    ├──> Task 04 (OpenAPI)
Task 02 (Validator) ┤
                    │
Task 03 (Max Len) ──┘
```

- Task 02 creates `IdentifierValidator` utility
- Tasks 01 and 03 can use this utility if Task 02 is done first
- Task 04 should be done last (documents all validation)

---

## Not Required for Production

These tasks are **optional enhancements**. The current implementation is:
- ✅ Secure (prevents path traversal, injection)
- ✅ Protocol-compliant (mostly - Task 03 adds protocol-specified max length)
- ✅ Production-ready
- ✅ Well-tested

These tasks add:
- 📈 Better test coverage
- 🛡️ Defensive depth
- 📚 Better documentation
- 🎯 Edge case handling

---

## Related Documentation

- **Full Analysis:** `docs/architecture/naming-conventions-analysis.md`
- **Quick Reference:** `protocol/NAMING_CONVENTIONS.md`
- **Protocol Spec (SPARQL):** `protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md` §14
- **Protocol Spec (GSP):** `protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md` §O

---

## Task Format

Each task file includes:
- **Objective:** What to accomplish
- **Background:** Why it matters
- **Implementation Plan:** Step-by-step instructions with code examples
- **Tests:** Unit and integration test examples
- **Acceptance Criteria:** Definition of done
- **References:** Related docs and specs

All tasks are designed to be completable in a single session with clear success criteria.

---

**Last Updated:** 2025-11-06
**Status:** Optional enhancements, not blocking
