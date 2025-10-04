# SPARQL Version Control Conformance Levels

## Overview

This server implements configurable conformance levels for the SPARQL 1.2 Protocol Version Control Extension. Feature flags control which capabilities are enabled at runtime.

## Conformance Levels

### Level 1 (Basic)

**Required features:**
- Commits (UUIDv7 identifiers)
- Branches
- History operations
- Query at branch/commit
- RDF Patch support
- Strong ETags
- Problem+json error responses

**Configuration:**
```yaml
vc:
  level: 1
  commits-enabled: true
  branches-enabled: true
  history-enabled: true
  rdf-patch-enabled: true
```

### Level 2 (Advanced)

**All Level 1 features plus:**
- Three-way merge
- Conflict detection and representation
- Fast-forward merge
- Revert operations
- Reset operations
- Tags (immutable refs)
- Cherry-pick
- Blame/annotate

**Configuration:**
```yaml
vc:
  level: 2
  # Level 1 features
  commits-enabled: true
  branches-enabled: true
  history-enabled: true
  rdf-patch-enabled: true
  # Level 2 features
  merge-enabled: true
  conflict-detection-enabled: true
  tags-enabled: true
  revert-enabled: true
  reset-enabled: true
  cherry-pick-enabled: true
  blame-enabled: true
```

## Discovery

Clients can discover server capabilities using `OPTIONS /sparql`:

```http
OPTIONS /sparql HTTP/1.1
Host: localhost:8080
```

Response:

```http
HTTP/1.1 200 OK
Allow: GET, POST, OPTIONS
Accept-Patch: text/rdf-patch
SPARQL-Version-Control: 1.0
SPARQL-VC-Level: 2
SPARQL-VC-Features: commits, branches, history, rdf-patch, merge, conflict-detection, tags, revert, reset, cherry-pick, blame
Link: </version>; rel="version-control"
```

## Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `vc.level` | int | 2 | Conformance level (1 or 2) |
| `vc.commits-enabled` | boolean | true | Enable commit operations (Level 1) |
| `vc.branches-enabled` | boolean | true | Enable branch operations (Level 1) |
| `vc.history-enabled` | boolean | true | Enable history operations (Level 1) |
| `vc.rdf-patch-enabled` | boolean | true | Enable RDF Patch format (Level 1) |
| `vc.merge-enabled` | boolean | true | Enable merge operations (Level 2) |
| `vc.conflict-detection-enabled` | boolean | true | Enable conflict detection (Level 2) |
| `vc.tags-enabled` | boolean | true | Enable tag operations (Level 2) |
| `vc.revert-enabled` | boolean | true | Enable revert operations (Level 2) |
| `vc.reset-enabled` | boolean | true | Enable reset operations (Level 2) |
| `vc.cherry-pick-enabled` | boolean | true | Enable cherry-pick operations (Level 2) |
| `vc.blame-enabled` | boolean | true | Enable blame/annotate (Level 2) |

## Feature Behavior

### Automatic Level Enforcement

When `vc.level=1` is set, all Level 2 features are automatically disabled regardless of individual property values. This ensures strict conformance to the Basic level.

### Selective Feature Disabling

At Level 2, individual features can be disabled while maintaining overall Level 2 conformance:

```yaml
vc:
  level: 2
  # Disable specific Level 2 features
  cherry-pick-enabled: false
  blame-enabled: false
```

This configuration advertises Level 2 conformance but with cherry-pick and blame features disabled.

## Examples

### Minimal Configuration (Level 1)

```yaml
vc:
  level: 1
```

All Level 1 features enabled, all Level 2 features disabled.

### Full Configuration (Level 2, Default)

```yaml
vc:
  level: 2
```

All features enabled (default behavior).

### Custom Configuration (Level 2, Selective)

```yaml
vc:
  level: 2
  tags-enabled: false
  cherry-pick-enabled: false
```

Level 2 with tags and cherry-pick disabled.

## Testing

Conformance behavior is tested in:
- `VersionControlPropertiesTest` - Configuration logic
- `SparqlControllerTest` - Unit tests for OPTIONS endpoint
- `SparqlOptionsIntegrationTest` - Integration tests with real configuration

Run tests:
```bash
mvn test
```
