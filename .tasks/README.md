# CHUCC Server - Implementation Tasks

**Last Updated**: 2025-10-10

## Overview

This directory contains detailed implementation tasks for CHUCC Server enhancements and polish work. All core features (GSP, Version Control, SPARQL Query/Update) are **COMPLETE**. These tasks focus on polish, testing, and operational improvements.

## Task Status Summary

| # | Task | Priority | Status | Effort | Notes |
|---|------|----------|--------|--------|-------|
| 01 | [Enable Skipped Tests](./01-enable-skipped-tests.md) | High | Ready | 2-3 hours | Remove outdated @Disabled annotations |
| 02 | [Validate SPARQL Query](./02-validate-sparql-query-implementation.md) | Critical | ✅ COMPLETE | 30 min | Documentation only |
| 03 | [SPARQL Query via POST](./03-optional-sparql-query-via-post.md) | Low | Optional | 4-6 hours | Optional feature, low usage |
| 04 | [Observability](./04-observability-implementation.md) | Medium | Ready | 1-2 days | Metrics, health checks, dashboards |

## Quick Start

### Immediate Next Steps (Recommended Order)

1. **Task 01**: Enable Skipped Tests (2-3 hours)
   - Quick win, increases test coverage
   - Validates no-op detection for SPARQL updates
   - Result: 863 passing tests (up from 859)

2. **Task 04**: Add Observability (1-2 days)
   - Production-critical monitoring
   - Metrics for performance analysis
   - Health checks for operations

3. **Task 03**: SPARQL Query via POST (OPTIONAL, 4-6 hours)
   - Only if users specifically request it
   - Only if long queries (>2048 chars) are needed
   - Can be deferred indefinitely

### For Completeness

If you want 100% SPARQL 1.1 Protocol compliance:
1. Complete Task 01 (enable tests)
2. Complete Task 03 (query via POST)
3. Complete Task 04 (observability)

Total effort: 1.5-3 days

### For Production Readiness

If you just need production-ready system:
1. Complete Task 01 (enable tests) - 2-3 hours
2. Complete Task 04 (observability) - 1-2 days

Total effort: 1.5-2.5 days
*Task 03 can be skipped - GET queries work for 99% of use cases*

## Current System Status

### ✅ Complete Features
- Graph Store Protocol (GSP) - All operations
- Version Control API - Branches, tags, commits, history, merge, revert, cherry-pick, squash, rebase
- SPARQL Query GET - Full implementation with time-travel
- SPARQL Update POST - Full implementation with no-op detection
- Event Sourcing - Kafka + RDFPatch
- CQRS Architecture - Command/Query separation
- Test Infrastructure - 859 tests passing, 5 skipped

### ❌ Known Gaps
- SPARQL Query via POST (application/sparql-query) - Returns 501
  - Impact: LOW - Rarely used, GET handles 99% of queries
  - See: Task 03

- 4 tests disabled with outdated messages in SparqlUpdateNoOpIntegrationTest
  - Impact: TEST COVERAGE - Features are actually implemented
  - See: Task 01

- 1 test disabled for quad format filtering in RdfPatchServiceTest
  - Impact: LOW - Nice-to-have feature, triple format works fine
  - Not prioritized

- No production metrics/monitoring
  - Impact: MEDIUM - Needed for operations
  - See: Task 04

## Task Details

### Task 01: Enable Skipped Tests

**Why Important**: Validates that SPARQL UPDATE no-op detection works correctly

**What's Needed**:
- Remove 4 `@Disabled` annotations from SparqlUpdateNoOpIntegrationTest
- Update class Javadoc
- Run tests to verify they pass
- Potentially fix dataset parameter or enable projector if tests fail

**Expected Result**: 863 passing tests (859 + 4)

**Full Details**: [01-enable-skipped-tests.md](./01-enable-skipped-tests.md)

---

### Task 02: Validate SPARQL Query Implementation

**Why Important**: Confirms GET /sparql is production-ready

**What's Needed**: Nothing - this is documentation only

**Status**: ✅ COMPLETE - Validation performed, GET /sparql is fully implemented with:
- 10 integration tests in SparqlQueryIntegrationTest
- 6 time-travel tests in TimeTravelQueryIntegrationTest
- 14 unit tests in SparqlQueryServiceTest
- All passing, zero skipped

**Full Details**: [02-validate-sparql-query-implementation.md](./02-validate-sparql-query-implementation.md)

---

### Task 03: SPARQL Query via POST (OPTIONAL)

**Why Low Priority**:
- Less than 1% of SPARQL clients use POST for queries
- GET implementation handles 99% of use cases
- Only needed for very long queries (>2048 chars)
- Can be added later without breaking changes

**What's Needed**:
- Add `handleQueryViaPost()` method
- Route based on Content-Type: application/sparql-query
- Mirror GET implementation logic
- Add 10 integration tests
- Update OpenAPI documentation

**When to Implement**:
- User specifically requests it
- Encountering queries too long for GET
- Want 100% SPARQL 1.1 Protocol compliance

**Full Details**: [03-optional-sparql-query-via-post.md](./03-optional-sparql-query-via-post.md)

---

### Task 04: Observability Implementation

**Why Important**: Production monitoring and operational insights

**What's Needed**:
- Add Spring Boot Actuator + Micrometer
- Implement 6 custom metric types:
  1. RDF patch application time
  2. SPARQL query performance (by type, format)
  3. SPARQL update performance
  4. Event projector lag
  5. Commit creation rate
  6. Dataset cache metrics
- Add health indicators (repositories, Kafka)
- Create Grafana dashboards (optional)

**Result**: Production-ready monitoring with metrics for:
- Performance analysis (query/update latency)
- Capacity planning (commit rates, cache usage)
- Issue detection (event lag, errors)

**Full Details**: [04-observability-implementation.md](./04-observability-implementation.md)

---

## Testing After Tasks

### After Task 01
```bash
mvn -q test -Dtest=SparqlUpdateNoOpIntegrationTest
# Expected: 4 tests passing

mvn -q clean install
# Expected: 863 tests passing, 1 skipped
```

### After Task 03 (Optional)
```bash
mvn -q test -Dtest=SparqlQueryPostIntegrationTest
# Expected: 10 tests passing

mvn -q clean install
# Expected: 873 tests passing (863 + 10), 1 skipped
```

### After Task 04
```bash
mvn -q test -Dtest=MetricsIntegrationTest
# Expected: Tests passing

curl http://localhost:3030/actuator/health
# Expected: {"status":"UP",...}

curl http://localhost:3030/actuator/prometheus
# Expected: Prometheus-formatted metrics
```

## Build Commands

### Quick Validation
```bash
# Static analysis (~30 seconds)
mvn -q clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check

# Unit tests only (~1 minute)
mvn -q test

# Full build (~1-2 minutes)
mvn -q clean install
```

### After Changes
```bash
# Test specific class
mvn -q test -Dtest=SparqlUpdateNoOpIntegrationTest

# Full build with all tests
mvn -q clean install
```

## Directory Structure

```
.tasks/
├── README.md                                    # This file
├── PROJECT_STATUS_AND_ROADMAP.md                # Overall status
├── 01-enable-skipped-tests.md                   # Ready to implement
├── 02-validate-sparql-query-implementation.md   # Complete (docs)
├── 03-optional-sparql-query-via-post.md         # Optional feature
└── 04-observability-implementation.md           # Production monitoring
```

## Related Documentation

### Architecture
- [docs/architecture/README.md](../docs/architecture/README.md) - System architecture
- [docs/architecture/cqrs-event-sourcing.md](../docs/architecture/cqrs-event-sourcing.md) - CQRS patterns

### Development
- [.claude/CLAUDE.md](../.claude/CLAUDE.md) - Development guidelines
- [docs/development/contributing.md](../docs/development/contributing.md) - Contribution guide
- [docs/development/quality-tools.md](../docs/development/quality-tools.md) - Code quality tools

### API
- [docs/api/openapi-guide.md](../docs/api/openapi-guide.md) - API documentation
- [api/openapi.yaml](../api/openapi.yaml) - OpenAPI specification

## Completion Criteria

### Minimum (Production Ready)
- [x] SPARQL Query GET implemented and tested (✅ Complete)
- [x] SPARQL Update POST implemented and tested (✅ Complete)
- [ ] Skipped tests enabled (Task 01)
- [ ] Observability added (Task 04)
- **Estimated**: 1.5-2.5 days

### Full Compliance (100% SPARQL 1.1 Protocol)
- [x] SPARQL Query GET (✅ Complete)
- [x] SPARQL Update POST (✅ Complete)
- [ ] SPARQL Query via POST (Task 03)
- [ ] Skipped tests enabled (Task 01)
- [ ] Observability added (Task 04)
- **Estimated**: 2.5-3.5 days

## Questions?

For questions about:
- **Architecture**: See docs/architecture/
- **Development**: See .claude/CLAUDE.md
- **Testing**: See docs/development/contributing.md
- **API**: See docs/api/openapi-guide.md

## History

- **2025-10-10**: Created task structure
  - Task 01: Enable skipped tests
  - Task 02: Validate SPARQL Query (complete)
  - Task 03: Optional SPARQL Query via POST
  - Task 04: Observability implementation
