# API Extensions

This implementation includes the following extensions beyond the SPARQL 1.2 Protocol Version Control Extension specification:

## GET /version/diff

**Purpose**: Compare two commits and return the changeset between them.

**Request**: `GET /version/diff?from={commitId}&to={commitId}`

**Response**: RDF Patch (text/rdf-patch)

**Status**: Extension (not in official spec)

**Compatibility**: Does not conflict with spec. Optional feature that can be disabled via configuration.

**Configuration**: Controlled by `vc.diff-enabled` property (default: true)

---

## GET /version/blame

**Purpose**: Get last-writer attribution for a resource.

**Request**: `GET /version/blame?subject={iri}`

**Response**: JSON with attribution information

**Status**: Extension (not in official spec)

**Compatibility**: Can be enabled/disabled via `vc.blame-enabled` property.

**Configuration**: Controlled by `vc.blame-enabled` property (default: depends on VC level)

---

## Notes

All extensions:
- Are optional and can be disabled via configuration
- Do not interfere with spec-compliant operations
- Follow the same error format (problem+json) as spec
- Are clearly marked in API documentation
- Return 404 Not Found when disabled
