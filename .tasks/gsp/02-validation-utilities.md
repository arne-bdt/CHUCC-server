# Task 02: Graph Selector Validation Utilities

## Objective
Create validation utilities for Graph Store Protocol request parameters (graph IRI, default flag, selectors).

## Background
GSP requests include:
- `graph={iri}` parameter for named graphs
- `default=true` parameter for default graph
- Version control selectors (`branch`, `commit`, `asOf`)

The existing `SelectorValidator` handles selector mutual exclusion. We need to extend validation for GSP-specific parameters.

## Tasks

### 1. Review Existing Validation
- Read `src/main/java/org/chucc/vcserver/util/SelectorValidator.java`
- Understand how selector validation currently works

### 2. Create GraphParameterValidator Utility
Create `src/main/java/org/chucc/vcserver/util/GraphParameterValidator.java`:
- Method: `validateGraphParameter(String graph, Boolean isDefault)`
  - Ensures exactly one of `graph` or `default=true` is provided
  - Validates graph IRI syntax if provided
  - Throws appropriate exception with `selector_conflict` code
- Method: `validateGraphIri(String iri)`
  - Validates IRI per RFC 3986/3987
  - Rejects invalid IRIs

### 3. Add Unit Tests
Create `src/test/java/org/chucc/vcserver/util/GraphParameterValidatorTest.java`:
- Test valid graph IRI
- Test valid default=true
- Test error when both provided
- Test error when neither provided
- Test invalid IRI syntax

### 4. Integration with Exception Handling
- Ensure GraphParameterValidator throws exceptions compatible with existing error handling
- Verify problem+json error format includes correct `code` field

## Acceptance Criteria
- [ ] GraphParameterValidator validates graph parameters per spec
- [ ] All unit tests pass with >90% coverage
- [ ] Validation errors return proper problem+json format
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
- Task 01 (domain model)

## Estimated Complexity
Low (2-3 hours)
