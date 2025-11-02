# "default" Keyword Usage Audit

## Executive Summary

This audit identifies all usages of the string "default" across the CHUCC-server codebase to address the namespace collision issue: using "default" as a convenience keyword prevents users from creating graphs literally named "default".

**Key Finding**: There are TWO distinct usages:
1. **SPARQL Protocol Standard**: `?default=true` parameter (W3C specification compliance)
2. **Custom Convenience Keyword**: `graph=default` for blame API (our addition, causes namespace collision)

---

## Category 1: SPARQL Graph Store Protocol Standard (CANNOT CHANGE)

### GraphStoreController.java

The `?default=true` parameter is **mandated by W3C SPARQL 1.1 Graph Store HTTP Protocol**.

**Locations**:
- Line 140, 158, 170: Documentation referencing `?default=true`
- Line 204: `@RequestParam(name = "default", required = false) Boolean isDefault,` (GET)
- Line 288: `@RequestParam(name = "default", required = false) Boolean isDefault,` (HEAD)
- Line 389: `@RequestParam(name = "default", required = false) Boolean isDefault,` (PUT)
- Line 562: `@RequestParam(name = "default", required = false) Boolean isDefault,` (POST)
- Line 716: `@RequestParam(name = "default", required = false) Boolean isDefault,` (DELETE)
- Line 867: `@RequestParam(name = "default", required = false) Boolean isDefault,` (PATCH)

**Usage Pattern**:
```http
GET /data?default=true          # Query default graph
GET /data?graph=<uri>           # Query named graph
```

**Analysis**:
- This is a **W3C standard parameter** for SPARQL 1.1 Graph Store HTTP Protocol
- **CANNOT be removed or changed** without breaking protocol compliance
- Uses `Boolean` parameter (`true`/`false`), NOT a URI string
- **Does NOT cause namespace collision** because:
  - It's a boolean flag, not a graph identifier
  - Users can still create a graph named `http://example.org/default`
  - The protocol uses `?default=true` (flag) vs `?graph=<uri>` (identifier) - mutually exclusive

**Recommendation**: ✅ **Keep as-is** (required for SPARQL compliance)

---

## Category 2: Custom Convenience Keyword (CAUSES NAMESPACE COLLISION)

### HistoryController.java - Blame API

**Location**: Lines 304, 305, 306, 319, 321, 322

**Code**:
```java
/**
 * @param graph graph IRI to blame (required, use "default" for default graph)
 * @param offset number of results to skip (default 0)
 * @param limit maximum results per page (default 100, max 1000)
 */
@Parameter(name = "graph", description = "Graph IRI (use 'default' for default graph)",
    required = true, example = "http://example.org/metadata")
@Parameter(name = "offset", description = "Number of results to skip (default: 0)")
@Parameter(name = "limit", description = "Max results per page (default: 100, max: 1000)")
```

**Usage Pattern**:
```http
GET /version/blame?graph=default&commit=...           # Our convenience keyword
GET /version/blame?graph=http://example.org/g&commit=... # Named graph
```

**Analysis**:
- This is a **custom convenience feature** we added (not in SPARQL spec)
- **CAUSES NAMESPACE COLLISION**: Users cannot create a graph named `http://example.org/default` and query it
- The `graph` parameter accepts both:
  - Keyword: `"default"` → converted to `urn:x-arq:DefaultGraph`
  - URI: `"http://..."` → used as-is

**Recommendation**: ❌ **REMOVE** - Replace with canonical URI requirement

---

### BlameService.java

**Locations**: Lines 65, 250, 252, 256, 277

**Code**:
```java
/**
 * @param graphIri the graph IRI (or "default" for default graph)
 */
private Node parseGraphIri(String graphIri) {
  if ("default".equalsIgnoreCase(graphIri)) {
    // For default graph, use Jena's default graph IRI
    // Quad.defaultGraphIRI (urn:x-arq:DefaultGraph)
    return Quad.defaultGraphIRI;
  }
  return NodeFactory.createURI(graphIri);
}
```

**Analysis**:
- Implements the convenience keyword parsing
- Directly supports the problematic API design

**Recommendation**: ❌ **REMOVE** - Require canonical URI `urn:x-arq:DefaultGraph`

---

## Category 3: OpenAPI Specification

### api/openapi.yaml

**Locations**:
- Line 49: `$ref: '#/components/parameters/DefaultGraphUri'` (SPARQL standard)
- Line 121-130: `default-graph-uri` parameter definition (SPARQL standard)
- Line 464-472: `DefaultGraphUri` parameter schema (SPARQL standard)

**Code**:
```yaml
DefaultGraphUri:
  name: default-graph-uri
  in: query
  description: Default graph URI(s) for the query
  schema:
    type: array
    items:
      type: string
      format: uri
```

**Analysis**:
- This is `default-graph-uri` parameter from SPARQL 1.1 Protocol
- **NOT the same as** `?default=true` from Graph Store Protocol
- **Does NOT cause namespace collision** - it's a parameter name, not a value
- Standard SPARQL parameter for query operations

**Recommendation**: ✅ **Keep as-is** (SPARQL standard)

---

## Category 4: Dataset Parameter Defaults (NOT GRAPH-RELATED)

### Multiple Controllers

Many controllers use `@RequestParam(defaultValue = "default")` for the **dataset** parameter:
- BatchGraphsController.java: Lines 69, 105
- BranchController.java: Lines 82, 144, 239, 258, 283
- AdvancedOpsController.java: Lines 95, 136, 184, 239, 297, 352, 410, 452, 510, 551
- CommitController.java: Lines 72, 132
- RefsController.java: Lines 40, 58
- SparqlController.java: Lines 139, 345
- GraphStoreController.java: Lines 200, 284, 385, 558, 712, 863
- MergeController.java: Line 93

**Analysis**:
- This is about the **dataset name**, NOT graph names
- Default dataset is called "default" (like default Git remote is "origin")
- **Does NOT cause namespace collision** for graphs
- Separate namespace from graphs

**Recommendation**: ✅ **Keep as-is** (different namespace)

---

## Category 5: Documentation References

### Various Files

**Findings**:
- Multiple references to "default branch", "default graph", "default values"
- Most are NOT about the "default" keyword for graph identification
- Just natural English usage in documentation

**Recommendation**: ✅ **Keep as-is** (documentation, not API)

---

## Category 6: Test Files

**Files Found**: 32 test files containing "default" (from earlier grep)

**Analysis**: Need to check if tests verify:
- Graph Store Protocol `?default=true` parameter (should keep)
- Blame API `graph=default` keyword (should update)
- Dataset defaults (not relevant)

**Action Required**: Update tests after API refactoring

---

## Summary and Recommendations

### Critical Changes Required

| Component | Location | Issue | Action |
|-----------|----------|-------|--------|
| Blame API | HistoryController.java:304-322 | "default" keyword causes namespace collision | Remove keyword support, require canonical URI |
| Blame Service | BlameService.java:256 | Implements problematic keyword parsing | Remove `if ("default"...)` block |
| Blame Docs | BlameService.java:65,250,252 | Documents removed feature | Update to require `urn:x-arq:DefaultGraph` |
| Tests | 32 test files | Test removed feature | Update to use canonical URI |

### No Changes Required

| Component | Reason |
|-----------|--------|
| Graph Store Protocol `?default=true` | W3C standard, uses boolean not identifier |
| OpenAPI `default-graph-uri` | SPARQL standard parameter name |
| Dataset `defaultValue = "default"` | Different namespace (dataset vs graph) |
| Documentation | Natural language, not API keywords |

---

## Proposed Refactoring Plan

### Task 1: Update Blame API Contract

**File**: `src/main/java/org/chucc/vcserver/controller/HistoryController.java`

**Change**: Update parameter documentation to require canonical URI

```java
/**
 * @param graph graph IRI to blame (required, use "urn:x-arq:DefaultGraph" for default graph)
 */
@Parameter(name = "graph", description = "Graph IRI (use 'urn:x-arq:DefaultGraph' for default graph)",
    required = true, example = "http://example.org/metadata")
```

### Task 2: Remove Keyword Parsing

**File**: `src/main/java/org/chucc/vcserver/service/BlameService.java`

**Change**: Remove convenience keyword parsing

```java
private Node parseGraphIri(String graphIri) {
  // Removed "default" keyword support to prevent namespace collision
  // Users must use canonical URI: urn:x-arq:DefaultGraph
  return NodeFactory.createURI(graphIri);
}
```

### Task 3: Update Tests

**Files**: 32 test files (focus on BlameEndpointIT.java)

**Change**: Replace `graph=default` with `graph=urn:x-arq:DefaultGraph`

### Task 4: Update DTO Documentation

**File**: `src/main/java/org/chucc/vcserver/dto/BlameResponse.java`

Already updated (completed in previous session)

---

## Protocol Compliance Analysis

### W3C SPARQL 1.1 Graph Store HTTP Protocol

The Graph Store Protocol defines TWO ways to identify the default graph:

1. **Direct URL**: `http://example.org/data` (without parameters) → default graph
2. **Query parameter**: `http://example.org/data?default` or `?default=true` → default graph

**Our Implementation**:
- ✅ We correctly implement `?default=true` (boolean parameter)
- ✅ This does NOT conflict with user graph names
- ✅ No changes required for protocol compliance

**Reference**: https://www.w3.org/TR/sparql11-http-rdf-update/#http-get

### Custom Extension: Blame API

Our Blame API is a **custom extension**, not part of SPARQL specification:
- We have freedom to design the API
- No protocol compliance concerns
- **Should prioritize user flexibility** over convenience

**Conclusion**: We can safely remove "default" keyword from Blame API without affecting SPARQL compliance.

---

## Impact Assessment

### Breaking Change: YES

Removing "default" keyword support is a **breaking change** for Blame API consumers.

**Mitigation**:
- This API was just released (2025-11-02)
- Likely zero external consumers at this early stage
- Better to fix now before widespread adoption

### Affected Endpoints

Only ONE endpoint affected:
- `GET /version/blame?graph=...`

All other endpoints:
- Graph Store Protocol continues to work with `?default=true`
- Dataset parameter continues to default to "default"
- No impact on SPARQL query/update operations

---

## Next Steps

1. ✅ **This audit** - Completed
2. ⏳ **User decision** - Review findings and approve approach
3. ⏳ **Implementation** - Execute Tasks 1-4 above
4. ⏳ **Testing** - Verify all tests pass with new API
5. ⏳ **Documentation** - Update API docs and examples

---

## Appendix: Detailed Grep Results

### Controllers with "default" keyword

<details>
<summary>Click to expand full grep results</summary>

```
GraphStoreController.java:204: @RequestParam(name = "default", required = false) Boolean isDefault,
GraphStoreController.java:288: @RequestParam(name = "default", required = false) Boolean isDefault,
GraphStoreController.java:389: @RequestParam(name = "default", required = false) Boolean isDefault,
GraphStoreController.java:562: @RequestParam(name = "default", required = false) Boolean isDefault,
GraphStoreController.java:716: @RequestParam(name = "default", required = false) Boolean isDefault,
GraphStoreController.java:867: @RequestParam(name = "default", required = false) Boolean isDefault,

HistoryController.java:304: @param graph graph IRI to blame (required, use "default" for default graph)
HistoryController.java:319: @Parameter(name = "graph", description = "Graph IRI (use 'default' for default graph)",

BlameService.java:256: if ("default".equalsIgnoreCase(graphIri)) {
```

</details>

---

**Audit completed**: 2025-11-02
**Total findings**: 100+ occurrences of "default" keyword
**Critical issues**: 2 (Blame API parameter, Blame Service parsing)
**Non-issues**: 98+ (SPARQL standard, dataset names, documentation)
