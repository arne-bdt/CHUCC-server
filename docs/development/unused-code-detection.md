# Unused Code Detection Configuration

This document explains how Maven's quality tools (Checkstyle and PMD) are configured to detect unused code, matching the warnings reported by VS Code / Eclipse JDT.

## Overview

VS Code uses Eclipse JDT (Java Development Tools) for code analysis, which detects issues like:
- Unused imports
- Unused local variables
- Unused private fields
- Unused private methods
- TODO/FIXME comments

Maven's default Checkstyle and PMD configurations **do not** detect all these issues. This project has custom configurations to match VS Code's behavior.

## Configuration Files

### 1. Checkstyle Configuration

**File**: [`checkstyle-custom.xml`](../../checkstyle-custom.xml)

**Detects**:
- ✅ Unused imports (`UnusedImports`)
- ✅ TODO/FIXME comments (`TodoComment`)

**Usage in pom.xml**:
```xml
<plugin>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <executions>
        <!-- Google Style checks -->
        <execution>
            <id>google-checks</id>
            <configuration>
                <configLocation>google_checks.xml</configLocation>
            </configuration>
        </execution>
        <!-- Additional unused code checks -->
        <execution>
            <id>unused-code-checks</id>
            <configuration>
                <configLocation>checkstyle-custom.xml</configLocation>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. PMD Configuration

**File**: [`pmd-custom-ruleset.xml`](../../pmd-custom-ruleset.xml)

**Detects**:
- ✅ Unused local variables (`UnusedLocalVariable`)
- ✅ Unused private fields (`UnusedPrivateField`)
- ✅ Unused private methods (`UnusedPrivateMethod`)
- ✅ Unnecessary imports (`UnnecessaryImport`)

**Usage in pom.xml**:
```xml
<plugin>
    <artifactId>maven-pmd-plugin</artifactId>
    <configuration>
        <rulesets>
            <ruleset>pmd-custom-ruleset.xml</ruleset>
        </rulesets>
    </configuration>
</plugin>
```

## Running Static Analysis

### Check All Quality Tools

```bash
# Phase 1: Static analysis (Checkstyle + PMD)
mvn -q compile checkstyle:check pmd:check

# Phase 2: Full build with tests
mvn -q clean install
```

### Check Individual Tools

```bash
# Checkstyle only
mvn -q checkstyle:check

# PMD only
mvn -q pmd:check

# SpotBugs only
mvn -q spotbugs:check
```

## Current Detection Status

### ✅ Detected by Maven

| Issue Type | Tool | VS Code Count | Maven Detection |
|------------|------|---------------|-----------------|
| Unused imports | Checkstyle + PMD | 52 | ✅ Yes |
| Unused local variables | PMD | (included in 52) | ✅ Yes |
| Unused private fields | PMD | (included in 52) | ✅ Yes |
| Unused private methods | PMD | (included in 52) | ✅ Yes |
| TODO comments | Checkstyle | 11 | ✅ Yes |

### ⚠️ Limitations

**PMD UnusedFormalParameter** is disabled by default because it has many false positives for:
- Interface implementations
- Method overrides
- Event handlers
- Framework callbacks

If you want to enable it, uncomment the rule in `pmd-custom-ruleset.xml`:
```xml
<rule ref="category/java/bestpractices.xml/UnusedFormalParameter">
    <priority>3</priority>
    <properties>
        <property name="checkAll" value="false"/>
    </properties>
</rule>
```

## Suppressing Violations

### Checkstyle Suppression

Use `@SuppressWarnings` or add to suppressions file:

```xml
<!-- checkstyle-suppressions.xml -->
<suppress checks="UnusedImports" files="GeneratedCode.java"/>
<suppress checks="TodoComment" files=".*Test\.java"/>
```

### PMD Suppression

Use `@SuppressWarnings`:

```java
@SuppressWarnings("PMD.UnusedPrivateField")
private String temporaryField;  // Used for debugging

@SuppressWarnings({"PMD.UnusedLocalVariable", "PMD.UnusedPrivateMethod"})
public void myMethod() {
    String unused = "debug";  // Intentionally unused for clarity
}
```

Or use `// NOPMD` inline comments:

```java
private void helperMethod() { // NOPMD - Used by reflection
    // ...
}
```

## Integration with CI/CD

The static analysis runs automatically during Maven's `validate` phase:

```yaml
# .github/workflows/build.yml
- name: Run static analysis
  run: mvn -q compile checkstyle:check pmd:check spotbugs:check

- name: Run tests
  run: mvn -q test
```

## Comparison with VS Code

### Why Doesn't Maven Use Eclipse JDT?

You can configure Maven to use the Eclipse JDT compiler instead of `javac`, but this has trade-offs:

**Pros**:
- ✅ Exact same warnings as VS Code
- ✅ Can reuse Eclipse settings files

**Cons**:
- ❌ Different compiler behavior than production
- ❌ Potential annotation processor compatibility issues
- ❌ Slower builds in some cases

**Our Approach**: Use standard `javac` + Checkstyle + PMD for broader compatibility, while achieving similar unused code detection.

## Further Reading

- [Checkstyle UnusedImports Documentation](https://checkstyle.sourceforge.io/checks/imports/unusedimports.html)
- [Checkstyle TodoComment Documentation](https://checkstyle.sourceforge.io/checks/misc/todocomment.html)
- [PMD Unused Code Rules](https://pmd.github.io/pmd/pmd_rules_java_bestpractices.html)
- [Quality Tools Guide](quality-tools.md)
