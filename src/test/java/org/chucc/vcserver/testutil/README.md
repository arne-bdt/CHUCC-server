# Test Utilities

Centralized test utilities and configurations for consistent, maintainable testing.

## Overview

This package provides:
- **Externalized configuration** via `test.properties`
- **Base test fixtures** for common setup/teardown
- **Test constants** for consistent test data
- **Container configurations** for integration testing

## Configuration (test.properties)

Test configuration is externalized in `src/test/resources/test.properties`:

```properties
# Testcontainers Configuration
# Using native image for faster startup and lower memory footprint
testcontainers.kafka.image=apache/kafka-native:4.1.0
testcontainers.kafka.reuse=false

# Test Data Configuration
test.dataset.default=default
test.dataset.test=test-dataset
test.branch.default=main
test.author.default=System
```

**Benefits:**
- Easy updates of Docker images without code changes
- Consistent configuration across all tests
- Environment-specific overrides possible

## Base Test Fixtures

### IntegrationTestFixture

Base class for integration tests that need repository cleanup and initial setup.

**Usage:**

```java
@SpringBootTest
@ActiveProfiles("it")
class MyIntegrationTest extends IntegrationTestFixture {

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void myTest() {
    // Repositories are already cleaned
    // initialCommitId is available
    // Initial commit and "main" branch created
  }
}
```

**Features:**
- Automatic cleanup of `BranchRepository`, `CommitRepository`, `TagRepository`
- Creates initial commit and branch (configurable)
- Provides helper methods for creating commits and patches
- Access to `initialCommitId` field

**Customization:**

```java
@Override
protected String getDatasetName() {
  return "my-dataset"; // Default: "default"
}

@Override
protected boolean shouldCreateInitialSetup() {
  return false; // Skip automatic setup
}

@Override
protected String getInitialBranchName() {
  return "develop"; // Default: "main"
}
```

**Helper Methods:**

```java
// Create a commit with RDF patch
CommitId commitId = createCommit(
  dataset,
  List.of(parentId),
  "Alice",
  "Add data",
  "TX .\nA <s> <p> \"value\" .\nTC ."
);

// Create a simple test patch
String patch = createSimplePatch(
  "http://example.org/s",
  "http://example.org/p",
  "value"
);
```

## Test Constants

### TestConstants

Centralized constants for consistent test data across all tests.

**Usage:**

```java
import static org.chucc.vcserver.testutil.TestConstants.*;

// Dataset names
String ds = DATASET_DEFAULT; // "default"
String test = DATASET_TEST;  // "test-dataset"

// Branch names
String main = BRANCH_MAIN;     // "main"
String feature = BRANCH_FEATURE; // "feature-branch"

// Authors
String alice = AUTHOR_ALICE; // "Alice <mailto:alice@example.org>"
String bob = AUTHOR_BOB;     // "Bob <mailto:bob@example.org>"

// RDF Content
String turtle = TURTLE_SIMPLE; // "@prefix ex: <http://example.org/> ..."
String patch = PATCH_SIMPLE;   // "TX .\nA <...> <...> \"...\" .\nTC ."
```

**Benefits:**
- Consistent test data across all tests
- Easy to find and update common values
- Reduces duplicate string literals

## Container Configurations

### KafkaTestContainers

Centralized Kafka container configuration for integration tests.

**Usage:**

```java
@Container
static final KafkaContainer kafka =
    KafkaTestContainers.createKafkaContainerNoReuse();

@DynamicPropertySource
static void kafkaProperties(DynamicPropertyRegistry registry) {
  registry.add("kafka.bootstrap-servers", kafka::getBootstrapServers);
  registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
}
```

**Benefits:**
- Consistent Kafka version across all tests (using native image 4.1.0)
- Configuration loaded from `test.properties`
- Easy updates (just change properties file)
- Proper error handling with fallback defaults
- Native image provides faster startup and lower memory footprint for tests

**Methods:**
- `createKafkaContainer()` - Standard container
- `createKafkaContainerNoReuse()` - Isolated container
- `getKafkaImage()` - Get configured image name

## Migration Guide

### Migrating Existing Tests

**Before:**

```java
@BeforeEach
void setUp() {
  branchRepository.deleteAllByDataset("default");
  commitRepository.deleteAllByDataset("default");

  CommitId initialId = CommitId.generate();
  Commit initial = new Commit(initialId, List.of(),
      "System", "Initial commit", Instant.now());
  commitRepository.save("default", initial, RDFPatchOps.emptyPatch());

  Branch main = new Branch("main", initialId);
  branchRepository.save("default", main);
}

private static final String TURTLE = "@prefix ex: <http://example.org/> .\n"
    + "ex:subject ex:predicate \"value\" .";
```

**After:**

```java
class MyTest extends IntegrationTestFixture {
  // No @BeforeEach needed - base class handles it
  // Use TestConstants.TURTLE_SIMPLE instead
  // Access initialCommitId from base class
}
```

### Updating Kafka Tests

**Before:**

```java
@Container
static final KafkaContainer kafka =
    new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
```

**After:**

```java
@Container
static final KafkaContainer kafka =
    KafkaTestContainers.createKafkaContainerNoReuse();
```

### Why Native Kafka Image?

The native Kafka image (`apache/kafka-native:4.1.0`) provides significant benefits for integration tests:

**Performance Benefits:**
- **Faster startup**: Native compilation eliminates JVM warmup time
- **Lower memory footprint**: Reduced overhead compared to JVM-based images
- **Consistent performance**: No JIT compilation delays during tests

**Compatibility:**
- Fully compatible with Testcontainers
- Same Kafka APIs and protocols as standard image
- KRaft mode support (no ZooKeeper required)

**When to use standard vs native:**
- **Native (recommended for tests)**: Fast startup, lower resource usage
- **Standard**: Only if you need JVM-specific features or debugging

To switch between images, simply update `test.properties`:
```properties
# For native image (recommended)
testcontainers.kafka.image=apache/kafka-native:4.1.0

# For standard JVM image
testcontainers.kafka.image=apache/kafka:4.1.0
```

## Best Practices

1. **Use IntegrationTestFixture** for any test needing repositories
2. **Use TestConstants** for common test data
3. **Use KafkaTestContainers** for Kafka integration tests
4. **Update test.properties** instead of hardcoding versions
5. **Override fixture methods** for custom behavior
6. **Add new constants** to TestConstants when repeated

## Examples

See these tests for examples:
- `GraphStorePutIntegrationTest` - Uses IntegrationTestFixture
- `ReadModelProjectorIT` - Uses KafkaTestContainers
- Various tests - Use TestConstants
