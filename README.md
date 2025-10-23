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

### Graph Store Protocol
- ✅ GET, PUT, POST, DELETE, PATCH, HEAD operations
- ✅ Named graph support (`?graph=<uri>` parameter)
- ✅ Default graph operations (`?default=true` parameter)
- ✅ Quad-based RDF Patch handling
- ✅ Full version control integration

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
- ✅ **Multi-dataset** - Dataset parameter support consistently across all endpoints

### Performance & Scalability
- ✅ **Snapshot Optimization** - Fast recovery and query materialization from snapshots
- ✅ **LRU Cache Eviction** - Bounded memory usage with Caffeine-based cache
- ✅ **Cache Metrics** - Monitor cache hit rates, evictions, and memory usage
- ✅ **On-demand Snapshot Loading** - Snapshots loaded from Kafka when needed (not stored in memory)
- ✅ **Metadata Caching** - Fast snapshot lookups with minimal memory footprint
- ✅ **Event Deduplication** - Exactly-once processing semantics with UUIDv7-based deduplication cache

### Operations
- ✅ **Branch Deletion** - Delete branches with protection for main branch
- ✅ **Dataset Deletion** - Delete entire datasets with optional Kafka topic cleanup
- ✅ **Confirmation Requirements** - Safeguards against accidental deletion
- ✅ **Audit Trail** - All operations recorded as events in Kafka



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
  -H "SPARQL-VC-Message: Add new person" \
  -H "SPARQL-VC-Author: alice@example.org" \
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
curl -X POST http://localhost:3030/sparql?branch=feature-x \
  -H "Content-Type: application/sparql-update" \
  -H "SPARQL-VC-Message: Experimental change" \
  -H "SPARQL-VC-Author: bob@example.org" \
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

- [SPARQL 1.2 Protocol with Version Control Extension](./.claude/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)

## Project Structure

```
api/                    # OpenAPI specification and JSON schemas
protocol/               # Protocol documentation
src/                    # Source code
docs/                   # Comprehensive documentation (see docs/README.md)
  architecture/         # System architecture (C4 model, CQRS guide)
  api/                  # API documentation (OpenAPI, error codes)
  development/          # Development guides (contributing, quality tools)
  operations/           # Operations guides (performance, deployment)
  conformance/          # Protocol conformance documentation
tests/                  # Test suites
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

✅ **Alpha** - Core functionality implemented, production-ready features

**Completed:**
- ✅ Protocol specification (SPARQL 1.2 + Version Control Extension)
- ✅ OpenAPI specification
- ✅ JSON schemas
- ✅ Core SPARQL endpoint (Query + Update)
- ✅ Graph Store Protocol (GET, PUT, POST, DELETE, PATCH, HEAD)
- ✅ Named graph support (quad-based RDF Patch handling)
- ✅ Version control layer (branches, commits, merges, tags)
- ✅ Storage backend (Apache Jena + Kafka event sourcing)
- ✅ Full CQRS + Event Sourcing architecture (command handlers → Kafka → projectors)
- ✅ Comprehensive test suite (~913 tests, including async event flow validation)
- ✅ Performance optimizations (snapshots, LRU cache)
- ✅ Deletion operations (branches, datasets)
- ✅ Time-travel query validation tests (5 comprehensive integration tests)
- ✅ Performance refactoring (Model API → Graph API migration complete)
- ✅ Consistent dataset parameter support (removed all hardcoded "default" values)

**Remaining Tasks:**
- 📋 Java API layer (programmatic access without HTTP)
- 📋 Kafka CQRS/ES best practices (partition keys, schema registry)

See [Task Roadmap](./.tasks/README.md) for detailed status and remaining work.

## Documentation

Comprehensive documentation is available in the [`docs/`](./docs/) directory:

- **[Documentation Index](./docs/README.md)** - Start here for navigation
- **[Architecture Guide](./docs/architecture/README.md)** - For AI agents and developers
- **[C4 Model](./docs/architecture/)** - System architecture diagrams
- **[API Documentation](./docs/api/)** - OpenAPI, error codes, extensions
- **[Development Guides](./docs/development/)** - Contributing, quality tools
- **[Operations Guides](./docs/operations/)** - Performance, deployment

## Contributing

Contributions welcome! Please see [Contributing Guide](./docs/development/contributing.md) for guidelines.

## License

Apache 2.0 - see [LICENSE](./LICENSE) for details.

## Acknowledgments

- Based on the SPARQL 1.2 Protocol (W3C)
- RDF Patch format from Apache Jena
- Inspired by Git version control model
