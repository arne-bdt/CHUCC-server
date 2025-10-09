# Code Quality and Security Tools

This project uses multiple tools to ensure code quality and security.

## Automated Tools (run during `mvn clean install`)

### 1. **Checkstyle** - Code Style
- Configuration: `google_checks.xml`
- Enforces: 2-space indentation, 100-char line length, Javadoc requirements
- Fails build on violations

### 2. **SpotBugs** - Bug Detection
- Effort: Max, Threshold: Low
- Detects common bug patterns and potential issues
- Fails build on violations

### 3. **PMD** - Static Analysis
- Ruleset: `quickstart.xml`
- Detects code smells, unused code, complexity issues
- Currently set to warn only (not fail build)
- Also runs CPD (Copy-Paste Detector) to find duplicated code

### 4. **JaCoCo** - Code Coverage
- Reports code coverage from unit and integration tests
- Minimum coverage: 40% instruction, 30% branch
- Fails build if coverage drops below threshold
- Report location: `target/site/jacoco/index.html`

### 5. **License Maven Plugin** - License Management
- Downloads and tracks dependency licenses
- Report location: `target/generated-sources/license/THIRD-PARTY.txt`

## Manual Tools

### OWASP Dependency-Check - Security Vulnerabilities
**Run manually to check for known CVEs in dependencies:**

```bash
# Check for vulnerabilities (fails on CVSS >= 7)
mvn dependency-check:check

# Generate report without failing build
mvn dependency-check:aggregate

# Report location: target/dependency-check-report.html
```

**Note:** First run downloads CVE database (~300MB) and takes 5-10 minutes.

### Versions Maven Plugin - Dependency Updates
**Check for outdated dependencies and plugins:**

```bash
# Check for dependency updates
mvn versions:display-dependency-updates

# Check for plugin updates
mvn versions:display-plugin-updates

# Check for property-driven version updates
mvn versions:display-property-updates

# Update to latest versions (creates backup pom)
mvn versions:use-latest-versions

# Update to latest releases (no snapshots)
mvn versions:use-latest-releases
```

## Report Locations

After `mvn clean install`, reports are available at:

- **JaCoCo Coverage**: `target/site/jacoco/index.html`
- **PMD Violations**: `target/pmd.xml` (XML) or `target/site/pmd.html` (HTML)
- **CPD Duplications**: `target/cpd.xml` (XML) or `target/site/cpd.html` (HTML)
- **SpotBugs**: `target/spotbugsXml.xml`
- **Checkstyle**: Console output during build
- **License Info**: `target/generated-sources/license/THIRD-PARTY.txt`

## Suppressing False Positives

### OWASP Dependency-Check
Edit `dependency-check-suppressions.xml`:

```xml
<suppress>
    <notes>False positive - not actually vulnerable</notes>
    <cve>CVE-2024-12345</cve>
</suppress>
```

### SpotBugs
Use `@SuppressFBWarnings` annotation:

```java
@SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Intentional exposure for DTO")
```

## Adjusting Thresholds

Edit `pom.xml`:

- **JaCoCo coverage**: Lines 275-284 (currently 40%/30%)
- **PMD fail on violation**: Line 227 (currently `false`)
- **OWASP CVSS threshold**: Line 299 (currently 7)
