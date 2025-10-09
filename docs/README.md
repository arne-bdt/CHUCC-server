# CHUCC Server Documentation

**Comprehensive documentation for the CHUCC Server - a SPARQL 1.2 Protocol implementation with Version Control**

## Quick Navigation

### For AI Agents & Developers
- **[Architecture Guide](architecture/README.md)** - Start here! Comprehensive overview for AI agents
- **[C4 Model](architecture/)** - System architecture at different abstraction levels
  - [Level 1: Context](architecture/c4-level1-context.md) - System context and external dependencies
  - [Level 2: Container](architecture/c4-level2-container.md) - High-level technology choices
  - [Level 3: Component](architecture/c4-level3-component.md) - Internal component structure
- **[CQRS & Event Sourcing](architecture/cqrs-event-sourcing.md)** - Core architecture pattern explained

### Development
- [Contributing Guide](development/contributing.md) - How to contribute to the project
- [Quality Tools](development/quality-tools.md) - Checkstyle, SpotBugs, PMD configuration
- [Token Optimization](development/token-optimization.md) - Efficient Maven commands for AI agents
- [Testing Strategy](../claude/CLAUDE.md) - Comprehensive testing guidelines (in .claude/CLAUDE.md)

### API Documentation
- [OpenAPI Guide](api/openapi-guide.md) - Using and maintaining OpenAPI documentation
- [Error Codes](api/error-codes.md) - RFC 7807 error code reference
- [API Extensions](api/api-extensions.md) - Version control extensions to SPARQL protocol
- [Spec Compliance](api/spec-compliance.md) - SPARQL 1.2 compliance status

### Operations
- [Performance](operations/performance.md) - Performance optimization and benchmarks
- Deployment Guide - (To be created)

### Conformance
- [Conformance Levels](conformance/conformance-levels.md) - Level 1 and Level 2 feature support

## Project Overview

**CHUCC Server** (Collaborative Hub for Unified Content Control) implements the SPARQL 1.2 Protocol with a Version Control Extension, enabling:

- **Graph Store Protocol (GSP)**: Full CRUD operations on RDF graphs
- **Version Control**: Git-like operations (branches, tags, commits, merge, revert, cherry-pick, etc.)
- **CQRS + Event Sourcing**: Commands create events, projectors update read models
- **Time-Travel Queries**: Query historical graph states with `asOf` selector
- **Conflict Detection**: Three-way merge with structured conflict representation

## Technology Stack

- Java 21 + Spring Boot 3.5
- Apache Jena 5.5 (in-memory RDF graphs)
- Apache Kafka (event store)
- RDFPatch (event format)
- JUnit 5 + AssertJ + Awaitility (testing)
- Testcontainers (integration tests)

## Quick Start

```bash
# Clone repository
git clone <repository-url>
cd CHUCC-server

# Build and run tests
mvn clean install

# Run server
mvn spring-boot:run

# Access OpenAPI documentation
open http://localhost:8080/swagger-ui.html
```

## Documentation Structure

This documentation is organized into logical sections:

### Architecture (`architecture/`)
Deep dive into system design, patterns, and decisions. **Start here if you're an AI agent** or new to the codebase.

### Development (`development/`)
Guidelines for contributors, quality tools, and best practices.

### API (`api/`)
API specifications, error handling, and protocol extensions.

### Operations (`operations/`)
Performance tuning, monitoring, and deployment.

### Conformance (`conformance/`)
Protocol conformance levels and feature support.

## Key Documents for Different Audiences

### For AI Agents (New to Codebase)
1. [Architecture Guide](architecture/README.md) - Comprehensive overview
2. [C4 Level 1: Context](architecture/c4-level1-context.md) - External view
3. [C4 Level 2: Container](architecture/c4-level2-container.md) - Technology stack
4. [CQRS & Event Sourcing](architecture/cqrs-event-sourcing.md) - Core pattern
5. [Testing Strategy](../claude/CLAUDE.md) - How to write tests

### For Contributors
1. [Contributing Guide](development/contributing.md)
2. [Quality Tools](development/quality-tools.md)
3. [Error Codes](api/error-codes.md)
4. [OpenAPI Guide](api/openapi-guide.md)

### For API Users
1. [Spec Compliance](api/spec-compliance.md)
2. [API Extensions](api/api-extensions.md)
3. [Error Codes](api/error-codes.md)
4. [Conformance Levels](conformance/conformance-levels.md)

### For Operations
1. [Performance](operations/performance.md)
2. [C4 Level 2: Container](architecture/c4-level2-container.md) - Deployment view

## External Resources

- [SPARQL 1.2 Protocol Specification](https://www.w3.org/TR/sparql12-protocol/)
- [Graph Store Protocol Specification](https://www.w3.org/TR/sparql12-graph-store-protocol/)
- [Version Control Extension Specification](../.claude/protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md)
- [Apache Jena Documentation](https://jena.apache.org/documentation/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)

## Getting Help

- **Architecture Questions**: Read [Architecture Guide](architecture/README.md)
- **API Questions**: Read [API Documentation](api/)
- **Testing Questions**: Read [Testing Strategy](../claude/CLAUDE.md)
- **Build Issues**: Read [Quality Tools](development/quality-tools.md)
- **Performance Issues**: Read [Performance Guide](operations/performance.md)

## Project Status

See [Project Status and Roadmap](../.tasks/PROJECT_STATUS_AND_ROADMAP.md) for current implementation status and remaining work.

## Contributing

See [Contributing Guide](development/contributing.md) for details on:
- Code style and quality requirements
- Testing requirements
- Pull request process
- Development workflow

## License

[Add license information here]
