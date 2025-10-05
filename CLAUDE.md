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

This project uses Checkstyle and SpotBugs for static code analysis.

**Checkstyle Rules:**
- **Indentation**: 2 spaces for code blocks, 4 spaces for continuation (wrapped parameters/arguments)
- **Line length**: Maximum 100 characters
- **Javadoc**: Required for all public classes, methods, and constructors (including parameterized constructors)
  - Must include @param tags for all parameters
  - Must include @return tag for non-void methods
- **Import order**: No blank lines between imports; order: external libs (edu.*, com.*), then java.*, then org.* (project packages)
  ```java
  import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
  import java.util.ArrayList;
  import java.util.List;
  import org.chucc.vcserver.dto.ConflictItem;
  ```

**SpotBugs Common Patterns:**
- **EI_EXPOSE_REP/EI_EXPOSE_REP2**: Use defensive copying for mutable collections/arrays
  - Getters: `return new ArrayList<>(internalList);`
  - Setters/constructors: `this.list = new ArrayList<>(list);`
- **SE_BAD_FIELD**: Make non-serializable exception fields `transient`
- **SE_TRANSIENT_FIELD_NOT_RESTORED**: Suppress with @SuppressFBWarnings if field is not actually serialized
- Use `@SuppressFBWarnings(value = "CODE", justification = "reason")` only when warnings are false positives

At the end of the task execute "mvn clean install" with only the added and modified tests first. Fix any warning and error, also those from Checkstyle and SpotBugs.
This should reduce token usage.
As last step run "mvn clean install" with all tests including the integration tests and fix any warning and any error. ("-DskipTests" is not allowed)

Build configuration:
- Use "mvn clean install" for normal builds (batch mode enabled by default via .mvn/maven.config)
- Use "mvn clean install -X" only when you need verbose/debug output to diagnose build issues
- The logback-test.xml configuration reduces Spring Boot, Kafka, and Testcontainers noise during tests

In the prompts I intentionally use words like "maybe" because I want to give you the opportunity to make alternative suggestions or propose variations if you think they are better.

Whenever you think a task is too big for one step:
- do not start right away to implement it, instead:
- create a folder with an ordered list of broken down ai-agent-friendly tasks in separate markdown files, which are small enough to be executed in one session
- making oversight easier and reducing token usage

After an implementation task, please provide me with a proper git commit message including a short description of what you have done.