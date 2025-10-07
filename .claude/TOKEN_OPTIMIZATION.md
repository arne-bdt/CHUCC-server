# Token Optimization Guide for Claude Code

This guide provides strategies to minimize token usage while maintaining code quality.

## Maven Build Optimization

### Always Use Quiet Mode (`-q`)

**Token Savings: ~99% for successful builds**

```bash
# ❌ Without -q: ~100,000 tokens
mvn clean install

# ✅ With -q: ~1,000 tokens
mvn -q clean install
```

### Build Phase Strategy

**Phase 1: Static Analysis (~30 seconds, minimal tokens)**
```bash
mvn -q clean compile checkstyle:check spotbugs:check pmd:check pmd:cpd-check
```
- Catches quality issues before running tests
- Zero output when successful
- Stops immediately on first violation

**Phase 2a: Incremental Tests (~10-30 seconds)**
```bash
mvn -q test -Dtest=NewTestClass,ModifiedTestClass
```
- Only run tests for modified code
- Skip if only docs/config changed

**Phase 2b: Full Build (~2 minutes)**
```bash
mvn -q clean install
```
- All 713 tests (698 unit + 15 integration)
- All quality gates enforced
- Minimal output on success

### When Investigating Failures

Re-run WITHOUT `-q` to see full details:

```bash
# Get full output for specific test
mvn test -Dtest=FailingTestClass

# Get full build output
mvn clean install

# Get debug output
mvn clean install -X
```

## File Operations

### Use Dedicated Tools (Not Bash Commands)

**❌ Token-Intensive:**
```bash
find . -name "*.java" | xargs cat    # Reads everything, huge tokens
ls -la src/                          # Lists all files, unnecessary
```

**✅ Token-Efficient:**
```bash
Glob("**/*.java")                    # Returns just file paths
Read("specific/file.java")           # Read exactly what you need
Grep("pattern", path="src/")         # Search without reading all files
```

### Read Files Strategically

**❌ Reading entire large files:**
```java
Read("/path/to/LargeClass.java")     // 5000 lines = many tokens
```

**✅ Reading specific sections:**
```java
Read("/path/to/LargeClass.java", offset=100, limit=50)  // Just what you need
```

**✅ Search first, then read:**
```java
// 1. Find files
Glob("**/PutGraphCommand*.java")

// 2. Grep for specific method
Grep("handle\\(", path="specific/file.java", output_mode="content", -n=true)

// 3. Read only relevant section
Read("file.java", offset=45, limit=20)
```

## Test Infrastructure

### Use Centralized Utilities

**❌ Duplicate setup in every test (~30 lines each):**
```java
@BeforeEach
void setUp() {
    branchRepository.deleteAllByDataset("default");
    commitRepository.deleteAllByDataset("default");
    // Create initial commit...
    // Create branch...
}
```

**✅ Extend IntegrationTestFixture:**
```java
class MyTest extends IntegrationTestFixture {
    // Automatic cleanup & setup!
    // Access to initialCommitId
}
```

**Token Savings:**
- Eliminates 30+ lines per test class
- 19 test classes = ~570 lines saved
- Reduces context needed in prompts

### Use TestConstants

**❌ Duplicate test data:**
```java
private static final String TURTLE = "@prefix ex: <http://example.org/> .\n"
    + "ex:subject ex:predicate \"value\" .";
```

**✅ Reference constants:**
```java
TestConstants.TURTLE_SIMPLE
TestConstants.AUTHOR_ALICE
TestConstants.PATCH_SIMPLE
```

## General Best Practices

### 1. Progressive Disclosure

Don't read everything upfront. Start narrow, expand as needed:

```java
// ✅ Good: Progressive approach
1. Glob to find relevant files
2. Grep to find specific patterns
3. Read only the relevant sections
4. Expand reading if needed

// ❌ Bad: Read everything
1. Read entire directory
2. Process everything
3. Use small portion
```

### 2. Avoid Redundant Operations

```bash
# ❌ Token-intensive: Running same tests multiple times
mvn test -Dtest=MyTest    # First run
mvn test -Dtest=MyTest    # Checking if it passed

# ✅ Token-efficient: Trust the exit code
mvn -q test -Dtest=MyTest  # Minimal output, trust result
```

### 3. Batch Related Operations

```bash
# ❌ Multiple separate commands
mvn compile
mvn checkstyle:check
mvn spotbugs:check

# ✅ Single combined command
mvn -q compile checkstyle:check spotbugs:check
```

### 4. Skip Unnecessary Steps

```bash
# For documentation-only changes
mvn -q compile checkstyle:check  # No need for tests

# For test-only changes
mvn -q test -Dtest=ChangedTest   # No need for full build until final verification
```

## Token Usage Metrics

### Typical Build Outputs

| Command | Without `-q` | With `-q` | Savings |
|---------|--------------|-----------|---------|
| `mvn compile checkstyle:check` | ~5,000 | ~50 | 99% |
| `mvn test -Dtest=OneTest` | ~2,000 | ~100 | 95% |
| `mvn clean install` | ~100,000 | ~1,000 | 99% |
| Phase 1 (static analysis) | ~15,000 | ~100 | 99.3% |
| Phase 2a (incremental tests) | ~10,000 | ~500 | 95% |

### Tool Usage Comparison

| Operation | Bad Approach | Tokens | Good Approach | Tokens | Savings |
|-----------|--------------|--------|---------------|--------|---------|
| Find Java files | `find + cat` | ~50,000 | `Glob + Read` | ~5,000 | 90% |
| Search for pattern | `grep -r` | ~30,000 | `Grep tool` | ~500 | 98% |
| Read large file | Read entire file | ~10,000 | Read section | ~1,000 | 90% |

## Quick Reference

### Most Important Rules

1. **Always use `mvn -q`** for all builds
2. **Use Phase 1 first** (static analysis before tests)
3. **Use Glob/Grep** instead of bash commands
4. **Read specific paths** when you know them
5. **Use test utilities** (IntegrationTestFixture, TestConstants)
6. **Only re-run without `-q`** when investigating failures

### When to Use Each Phase

- **Phase 1 only**: After code changes, before writing tests
- **Phase 2a only**: After implementing new feature with tests
- **Phase 2b (full build)**: Final verification before completion
- **No `-q`**: Only when debugging failures

## Configuration

All optimizations are already configured in the project:

- ✅ Maven batch mode enabled (`.mvn/maven.config`)
- ✅ Test logging reduced (`logback-test.xml`)
- ✅ Test config externalized (`test.properties`)
- ✅ Native Kafka image for faster tests (37% faster)
- ✅ Test utilities to eliminate duplication

**Result: Workflow is optimized for minimal token usage while maintaining quality.**
