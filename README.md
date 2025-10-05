# CHUCC Server

**Versioned SPARQL server implementing SPARQL 1.2 Protocol with Version Control Extension**

CHUCC-server is a versioned, in-memory SPARQL server with CQRS + event sourcing, using RDF Patch and Kafka to replay, branch, and time-travel queries—fast like Redis, precise like Git.

## Overview

This server implements:
- **SPARQL 1.2 Protocol** - Complete query and update operations
- **Version Control Extension** - Git-like branching, commits, merges, and time-travel
- **RDF Patch** - Efficient changeset format for versioned updates
- **Backward Compatibility** - Non-versioned SPARQL 1.1/1.2 clients work seamlessly

## Features

### Core SPARQL Protocol
- ✅ SPARQL Query (SELECT, CONSTRUCT, ASK, DESCRIBE)
- ✅ SPARQL Update (INSERT, DELETE, MODIFY)
- ✅ Content negotiation (JSON, XML, CSV, TSV)
- ✅ Graph protocol support (default-graph-uri, named-graph-uri)
- ✅ Both GET and POST methods

### Version Control
- ✅ **Branches** - Create, list, switch, delete, reset
- ✅ **Commits** - Atomic updates with UUIDv7 identifiers, metadata, and provenance
- ✅ **History** - Browse commit history with filtering and pagination
- ✅ **Time-travel** - Query dataset state at any point in time (`asOf`)
- ✅ **Merging** - Three-way merge with conflict detection
- ✅ **Tags** - Immutable named snapshots
- ✅ **Diff** - Compare any two commits
- ✅ **Blame** - Last-writer attribution per triple
- ✅ **Batch operations** - Apply multiple updates atomically

### Advanced Features
- ✅ **Optimistic Concurrency** - ETags and If-Match headers
- ✅ **Fast-forward merges** - Automatic when possible
- ✅ **Conflict detection** - Structured representation of merge conflicts
- ✅ **RFC 7807 Problem Details** - Standardized error responses
- ✅ **Multi-tenant** - Dataset scoping support



### Example: Basic Query

```bash
# Query the default branch
curl -X GET "http://localhost:3030/sparql?query=SELECT+*+WHERE+{+?s+?p+?o+}+LIMIT+10" \
  -H "Accept: application/sparql-results+json"
```

### Example: Versioned Update

```bash
# Create a commit on main branch
curl -X POST http://localhost:3030/sparql \
  -H "Content-Type: application/sparql-update" \
  -H "SPARQL-VC-Commit-Message: Add new person" \
  -H "SPARQL-VC-Commit-Author: alice@example.org" \
  --data-binary @- <<'EOF'
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
INSERT DATA {
  <http://example.org/alice> foaf:name "Alice" .
  <http://example.org/alice> foaf:age 30 .
}
EOF
```

### Example: Time-Travel Query

```bash
# Query state as of yesterday
curl -X GET "http://localhost:3030/sparql?query=SELECT+*+WHERE+{+?s+?p+?o+}&asOf=2025-10-03T12:00:00Z" \
  -H "Accept: application/sparql-results+json"
```

### Example: Branch and Merge

```bash
# Create a new branch
curl -X POST http://localhost:3030/version/branches \
  -H "Content-Type: application/json" \
  -d '{"name": "feature-x", "from": "main"}'

# Update on feature branch
curl -X POST http://localhost:3030/sparql \
  -H "Content-Type: application/sparql-update" \
  -H "SPARQL-VC-Branch: feature-x" \
  -H "SPARQL-VC-Commit-Message: Experimental change" \
  -H "SPARQL-VC-Commit-Author: bob@example.org" \
  --data-binary "INSERT DATA { ... }"

# Merge back to main
curl -X POST http://localhost:3030/version/merge \
  -H "Content-Type: application/json" \
  -d '{"from": "feature-x", "into": "main", "strategy": "three-way"}'
```

## API Documentation

See the complete OpenAPI 3.1 specification: [`api/openapi.yaml`](./api/openapi.yaml)

Interactive API documentation (when server is running):
- Swagger UI: http://localhost:3030/api-docs
- ReDoc: http://localhost:3030/redoc

## Protocol Documentation

- [SPARQL 1.2 Protocol with Version Control Extension](./protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

## Project Structure

```
api/                    # OpenAPI specification and JSON schemas
protocol/               # Protocol documentation
src/                    # Source code (to be implemented)
tests/                  # Test suites
docs/                   # Additional documentation
```


## Conformance

This implementation aims for:
- **SPARQL 1.2 Protocol** conformance (Level 1: Basic, Level 2: Advanced)
- **RDF Patch** format support (text/rdf-patch)
- **RFC 7807** Problem Details for errors
- **UUIDv7** for commit identifiers

## Technology Stack

- Language: Java
- Runtime: Java 21
- Framework: Spring Boot 3.5
- RDF + SPARQL: Apache Jena 5.5
- Persistent Event Sourcing: Kafka integration

## Building

### Quick Build
```bash
mvn clean install
```

The project uses batch mode by default for cleaner console output (configured in `.mvn/maven.config`).

### Build Options

**Verbose output** (when debugging):
```bash
mvn clean install -X
```

**Extra quiet** (minimal output):
```bash
mvn clean install -q
```

**Debug with full stack traces**:
```bash
mvn clean install -X -e
```

### Test Logging

Test execution logging is configured in `src/test/resources/logback-test.xml`:
- Application logs: INFO level
- Spring Boot/Kafka/Testcontainers: WARN level (reduces noise)

To temporarily increase test logging, override in your test:
```java
@SpringBootTest(properties = {"logging.level.org.springframework=DEBUG"})
```

## Development Status

🚧 **Pre-alpha** - API specification complete, implementation in progress

- [x] Protocol specification
- [x] OpenAPI specification
- [x] JSON schemas
- [ ] Core SPARQL endpoint
- [ ] Version control layer
- [ ] Storage backend
- [ ] Test suite

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## License

Apache 2.0 - see [LICENSE](./LICENSE) for details.

## Acknowledgments

- Based on the SPARQL 1.2 Protocol (W3C)
- RDF Patch format from Apache Jena
- Inspired by Git version control model
