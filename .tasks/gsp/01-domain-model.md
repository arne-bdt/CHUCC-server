# Task 01: Domain Model and Graph Representation

## Objective
Define domain model for graph resources and their version control properties.

## Background
The Graph Store Protocol manages named graphs and a default graph. Each graph is identified by an IRI (or default graph flag). In version control context, each graph has a history tracked via commits.

## Tasks

### 1. Review Existing Domain Model
- Examine `src/main/java/org/chucc/vcserver/domain/Commit.java`
- Examine `src/main/java/org/chucc/vcserver/domain/Branch.java`
- Understand how RDF Patch is currently stored/used in commits

### 2. Decide on Graph Representation
Options:
- **Option A**: Store graphs implicitly via RDF Patch events (compute on read)
- **Option B**: Store graph snapshots in read model (requires projector)

**Recommendation**: Option A aligns with event sourcing pattern. Use DatasetService to materialize graphs from patch history.

### 3. Add Graph-Specific Value Objects (if needed)
Consider adding:
- `GraphIdentifier` value object (wraps IRI or "default" flag)
- Validation for graph IRIs per RFC 3986/3987

### 4. Update Existing Domain Classes (if needed)
- Ensure Commit domain object supports graph-level operations
- Add graph field to patch metadata if not present

### 5. Write Unit Tests
- Test GraphIdentifier validation (valid IRI, default flag)
- Test equality and hashCode for GraphIdentifier

## Acceptance Criteria
- [ ] Domain model supports graph identification (IRI or default)
- [ ] Graph identifiers are validated according to spec
- [ ] All unit tests pass
- [ ] Zero Checkstyle/SpotBugs violations

## Dependencies
None (foundational task)

## Estimated Complexity
Low (1-2 hours)
