# Test Error Suppression

## Problem

Previously, loggers `org.apache.jena.riot` and `org.chucc.vcserver.projection.ReadModelProjector` were set to `level="OFF"` in [logback-test.xml](../../../resources/logback-test.xml), completely hiding ALL errors including real unexpected ones. This was dangerous in an async CQRS architecture where errors might occur asynchronously and be silently hidden.

## Solution

Implemented thread-local, message-based error suppression using SLF4J MDC:

1. **Individual tests specify exact errors to suppress** - no global suppression
2. **Thread-local** - suppression only affects the specific test that set it up
3. **Automatic cleanup** - try-with-resources ensures suppression ends when test completes
4. **Third-party library support** - works with Jena RIOT, RDFPatch, and any other library

## Usage

### Basic Example

```java
import static org.chucc.vcserver.testutil.ExpectedErrorContext.suppress;

@Test
void testInvalidIri() {
  // Suppress only errors containing "Bad character in IRI"
  try (var ignored = suppress("Bad character in IRI")) {
    GraphIdentifier.named("invalid iri"); // Error suppressed
  }
  // Suppression ends here - all other errors are visible
}
```

### Multiple Patterns

```java
@Test
void testMultipleErrors() {
  try (var ignored = suppress(
      "Database connection failed",
      "Unexpected null value",
      "Code 'INVALID' not recognized")) {
    // Code that triggers these specific errors
  }
}
```

### Important Notes

1. **Add @SuppressWarnings("try")** to suppress compiler warning about unused `ignored` variable:
   ```java
   @Test
   @SuppressWarnings("try")  // Suppress "resource never referenced" - used for MDC side-effects
   void myTest() {
     try (var ignored = suppress("Expected error")) {
       // test code
     } catch (Exception e) {
       throw new RuntimeException(e);
     }
   }
   ```

2. **Only suppresses matching errors** - if your test triggers an unexpected error with different text, it WILL be visible

3. **Thread-local** - other tests (even running concurrently) are not affected

## Implementation Details

### Components

1. **[ExpectedErrorContext](ExpectedErrorContext.java)** - Utility for setting up suppression
2. **[ExpectedErrorSuppressionFilter](ExpectedErrorSuppressionFilter.java)** - Logback filter that checks MDC

### How It Works

1. Test calls `ExpectedErrorContext.suppress("pattern")`
2. Patterns are stored in thread-local MDC storage
3. When error is logged, `ExpectedErrorSuppressionFilter` checks MDC
4. If error message contains any pattern, it's suppressed (DENY)
5. When try-with-resources closes, MDC is cleaned up

### Configuration

The filter is configured in [logback-test.xml](../../../resources/logback-test.xml):

```xml
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <!-- Thread-local filter for suppressing expected errors in specific tests -->
    <filter class="org.chucc.vcserver.testutil.ExpectedErrorSuppressionFilter"/>
    <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

## Examples in Codebase

See these tests for real examples:

- [GraphIdentifierTest.java](../../domain/GraphIdentifierTest.java) - Suppresses Jena RIOT validation errors
- [GraphParameterValidatorTest.java](../../util/GraphParameterValidatorTest.java) - Suppresses IRI validation errors
- [ReadModelProjectorExceptionHandlingTest.java](../../projection/ReadModelProjectorExceptionHandlingTest.java) - Suppresses projector exception handling errors
- [ReadModelProjectorTest.java](../../projection/ReadModelProjectorTest.java) - Suppresses deduplication warnings

## Migration from Old Approach

**Before** (global suppression - DANGEROUS):
```xml
<logger name="org.apache.jena.riot" level="OFF"/>
```

**After** (test-specific suppression - SAFE):
```xml
<logger name="org.apache.jena.riot" level="WARN"/>
```

```java
@Test
@SuppressWarnings("try")
void test() {
  try (var ignored = suppress("Bad character in IRI")) {
    // Test code
  } catch (Exception e) {
    throw new RuntimeException(e);
  }
}
```

## Benefits

1. **Safety** - Real unexpected errors are now visible immediately
2. **Precision** - Only suppresses exact errors you specify
3. **Context-awareness** - Each test controls its own suppression
4. **Thread-safety** - No interference between parallel tests
5. **Works with third-party code** - Suppresses errors from Jena, Kafka, etc.
