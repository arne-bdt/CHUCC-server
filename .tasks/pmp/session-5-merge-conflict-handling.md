# Session 5: Merge Conflict Handling (Optional Enhancement)

**Status:** Deferred
**Estimated Time:** 3-4 hours
**Priority:** Low (Future Enhancement)
**Dependencies:** Sessions 1-4, Merge API

---

## Overview

Enhance merge conflict detection to specifically identify and report prefix conflicts. Currently, prefix conflicts are handled as generic patch conflicts.

**Goal:** Specialized conflict reporting for prefix mismatches.

---

## Current Behavior

When same prefix points to different IRIs in two branches:

```
Branch main:  PA foaf: <http://xmlns.com/foaf/0.1/> .
Branch dev:   PA foaf: <http://example.org/my-foaf#> .

Merge dev → main → Generic conflict
```

**Response:**
```json
{
  "type": "/problems/merge-conflict",
  "conflicts": [
    {
      "graph": "urn:x-arq:DefaultGraph",
      "conflictingQuads": 1
    }
  ]
}
```

---

## Enhanced Behavior (This Session)

**Response with prefix-specific conflict:**
```json
{
  "type": "/problems/merge-conflict",
  "conflicts": [
    {
      "type": "prefix",
      "prefix": "foaf",
      "ours": "http://xmlns.com/foaf/0.1/",
      "theirs": "http://example.org/my-foaf#"
    }
  ]
}
```

---

## Implementation Tasks

### 1. Detect Prefix Conflicts (90 minutes)

Add to `MergeCommandHandler`:

```java
private List<PrefixConflict> detectPrefixConflicts(
    DatasetGraph base,
    DatasetGraph ours,
    DatasetGraph theirs) {

  Map<String, String> basePrefixes = extractPrefixes(base);
  Map<String, String> ourPrefixes = extractPrefixes(ours);
  Map<String, String> theirPrefixes = extractPrefixes(theirs);

  List<PrefixConflict> conflicts = new ArrayList<>();

  for (String prefix : theirPrefixes.keySet()) {
    String theirIRI = theirPrefixes.get(prefix);
    String ourIRI = ourPrefixes.get(prefix);
    String baseIRI = basePrefixes.get(prefix);

    // Conflict: Both changed prefix to different values
    if (ourIRI != null && !ourIRI.equals(theirIRI) && !ourIRI.equals(baseIRI)) {
      conflicts.add(new PrefixConflict(prefix, ourIRI, theirIRI));
    }
  }

  return conflicts;
}
```

---

### 2. Update MergeConflict DTO (20 minutes)

```java
public record MergeConflict(
    ConflictType type,
    String graph,           // For quad conflicts
    int conflictingQuads,   // For quad conflicts
    String prefix,          // For prefix conflicts
    String ours,            // For prefix conflicts
    String theirs           // For prefix conflicts
) {
  public enum ConflictType {
    QUAD,
    PREFIX
  }
}
```

---

### 3. Resolution Strategies (60 minutes)

Support prefix-specific resolution:

```json
{
  "sourceBranch": "dev",
  "resolutions": {
    "prefixes": {
      "foaf": "http://xmlns.com/foaf/0.1/"  // Resolve to this IRI
    }
  }
}
```

---

### 4. Tests (60 minutes)

```java
@Test
void merge_shouldDetectPrefixConflict() {
  // Arrange: Create conflicting prefixes
  definePrefixesOnBranch("main", Map.of("foaf", "http://xmlns.com/foaf/0.1/"));
  definePrefixesOnBranch("dev", Map.of("foaf", "http://example.org/my-foaf#"));

  // Act
  ResponseEntity<String> response = mergeBranch("dev", "main");

  // Assert
  assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  assertThat(response.getBody()).contains("\"type\":\"prefix\"");
  assertThat(response.getBody()).contains("\"prefix\":\"foaf\"");
}

@Test
void merge_shouldResolvePrefixConflict_withOursStrategy() {
  // Arrange: Conflict
  createPrefixConflict();

  // Act: Merge with resolution
  MergeRequest request = new MergeRequest(
      "dev",
      "ours",  // Keep our prefix
      null
  );
  mergeBranch(request);

  // Assert: Our prefix kept
  assertThat(getPrefixes().prefixes().get("foaf"))
      .isEqualTo("http://xmlns.com/foaf/0.1/");
}
```

---

## Files to Modify

```
src/main/java/org/chucc/vcserver/
  ├── command/
  │   └── MergeCommandHandler.java       # Add prefix conflict detection
  ├── dto/
  │   └── MergeConflict.java             # Add PREFIX type
  └── util/
      └── MergeUtil.java                 # Add prefix merge logic

src/test/java/org/chucc/vcserver/integration/
  └── MergeOperationsIT.java             # Add prefix conflict tests
```

---

## Success Criteria

- ✅ Prefix conflicts detected and reported separately
- ✅ Resolution strategies work for prefixes
- ✅ Manual resolution supported
- ✅ Tests pass (6+ new tests)

---

## Why Deferred?

1. **Generic conflict handling works:** Current merge already detects conflicts (as quad-level)
2. **Low priority:** Prefix conflicts are rare in practice
3. **Complex implementation:** Requires changes to merge algorithm
4. **Better DX:** Nice-to-have for better error messages, not critical

**Recommendation:** Implement only if users report confusion with generic conflict messages.

---

**Status:** Optional enhancement - defer until after core PMP features deployed and user feedback collected.
