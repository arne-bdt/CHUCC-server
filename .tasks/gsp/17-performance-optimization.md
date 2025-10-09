# Task 17: Performance Baseline and Observability

## Objective
Establish performance observability and baseline measurements for future optimization decisions.

## Background
At this implementation stage, in-memory RDF patch application should be fast. Rather than prematurely optimizing, we should:
1. Add observability (metrics) to measure actual performance
2. Rely on the existing simple cache in DatasetService
3. Defer sophisticated caching until data shows it's needed

## Rationale: Avoiding Premature Optimization

**Why NOT add Caffeine/Spring Cache now:**
- Materializing in-memory datasets from RDFPatches is already fast
- DatasetService already has a simple `ConcurrentHashMap` cache
- No performance data indicates caching is a bottleneck
- Adds complexity without proven benefit

**What IS reasonable at this stage:**
- ✅ **Metrics**: Observability to detect future issues (Micrometer via Spring Boot Actuator)
- ✅ **Simple manual cache**: The existing `ConcurrentHashMap` in DatasetService
- ⚠️ **Defer**: Sophisticated caching strategies until performance data justifies them

## Tasks

### 1. Verify Observability Infrastructure
Confirm existing setup:
- Spring Boot Actuator already included (provides Micrometer)
- Metrics endpoint already exposed: `/actuator/metrics`
- No additional dependencies needed

### 2. Document Current Architecture
Document DatasetService caching strategy:
- Manual `ConcurrentHashMap` cache for materialized datasets
- Cache key: `(datasetName, commitId)`
- Simple, sufficient for current needs

### 3. Add Basic Performance Metrics (Optional)
**Only if trivial to add:**
- Consider adding timer metrics for materialization operations
- Use existing Micrometer integration
- Keep it minimal (1-2 metrics maximum)

### 4. Document When to Revisit
Create guidance for future optimization:
- When to add sophisticated caching (performance data showing bottleneck)
- What metrics to monitor
- Decision criteria for optimization

## Acceptance Criteria
- [x] Observability infrastructure verified (Spring Boot Actuator)
- [x] Current caching strategy documented
- [ ] Decision criteria for future optimization documented
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 16 (event projector)

## Estimated Complexity
Low (1-2 hours) - Mainly documentation

## Notes
**Key principle**: Measure first, optimize later.
- Don't add caching complexity without performance data
- In-memory operations are fast - optimization may never be needed
- If performance issues arise, metrics will guide optimization decisions
