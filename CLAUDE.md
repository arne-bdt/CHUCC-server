This project shall implement [SPARQL 1.2 Protocol](https://www.w3.org/TR/sparql12-protocol/) and the [Version Control Extension](./protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md).

The basic technology stack is:
- Java 21 + Spring Boot 3.5 
- using Apache Jena 5.5 - supporting only in-memory graphs based on org.apache.jena.sparql.core.mem.DatasetGraphInMemory (like Apache Jena Fuseki) 
- implementing a CQRS-pattern with Event-Sourcing 
- RDFPatch from "jena-rdfpatch" for the events 
- store the events in Apache Kafka with an appropriate topic structure and setup

Prefer JUnit and Mockito for testing.
Use a test-driven development (TDD) approach. Write unit tests and integration tests for each feature before implementing it.
You may add additional tests after implementing a feature to increase coverage.

Always run "maven clean install" at the end of each task and fix any warning and error. ("-DskipTests" is not allowed) 

