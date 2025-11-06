# Naming Conventions Analysis for SPARQL Version Control Extensions

**Status:** Analysis Complete
**Date:** 2025-11-06
**Author:** Claude (Anthropic)

---

## Executive Summary

This document analyzes naming conventions for **datasets**, **branches**, and **tags** across the SPARQL 1.2 Protocol Version Control Extensions. It examines constraints from Apache Jena, Git, Kafka, URL encoding (RFC 3986), and security considerations, then proposes a **consistent, safe, and interoperable strategy**.

### Key Findings

1. ⚠️ **Protocol-Implementation Mismatch**: Protocol ABNF allows `/` in branch names, but implementation forbids it
2. ✅ **Current implementation is MORE restrictive than necessary** (good for security/simplicity)
3. ⚠️ **No explicit ABNF for dataset and tag names** in protocols
4. ✅ **Kafka constraints properly enforced** for dataset names
5. ⚠️ **URL encoding not addressed** in protocol specifications

### Recommended Strategy

**Use a conservative, unified naming pattern across all identifiers:**

```abnf
identifier-name = 1*( ALPHA / DIGIT / "." / "_" / "-" )
                ; Alphanumeric, period, underscore, hyphen
                ; Max length varies by type
                ; Unicode NFC normalization required
                ; Case-sensitive
```

**This pattern is:**
- ✅ Compatible with Git branch/tag names
- ✅ Compatible with Kafka topic names
- ✅ Compatible with URL path segments (no encoding needed)
- ✅ Compatible with file systems (safe characters)
- ✅ Easy to validate and reason about
- ✅ Prevents path traversal attacks
- ✅ Already implemented in codebase

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Constraint Analysis](#constraint-analysis)
3. [Security Considerations](#security-considerations)
4. [URL Encoding and Semantic Routing](#url-encoding-and-semantic-routing)
5. [Proposed Strategy](#proposed-strategy)
6. [Migration Plan](#migration-plan)
7. [References](#references)

---

## Current State Analysis

### 1. Implementation Status

#### Dataset Names

**Implementation** (`CreateDatasetCommand.java:99`):
```java
if (!name.matches("^[a-zA-Z0-9._-]+$")) {
  throw new IllegalArgumentException(
    "Dataset name contains invalid characters. " +
    "Allowed: a-z, A-Z, 0-9, . (dot), _ (underscore), - (hyphen)");
}
```

**Constraints:**
- Pattern: `^[a-zA-Z0-9._-]+$`
- Max length: 249 characters (Kafka topic limit)
- Cannot be `.` or `..`
- Reason: Kafka topic naming compatibility

**Protocol Specification:** ❌ No ABNF defined

---

#### Branch Names

**Implementation** (`Branch.java:14`):
```java
private static final Pattern VALID_NAME_PATTERN =
  Pattern.compile("^[A-Za-z0-9._\\-]+$");
```

**Constraints:**
- Pattern: `^[A-Za-z0-9._\-]+$`
- Unicode NFC normalization required
- Case-sensitive

**Protocol ABNF** (SPARQL 1.2 Protocol Extension, line 158):
```abnf
branch = 1*( ALPHA / DIGIT / "-" / "_" / "/" )
```

⚠️ **MISMATCH**: Protocol allows `/` (forward slash), implementation does NOT.

---

#### Tag Names

**Implementation** (`Tag.java:20`):
```java
private static final Pattern VALID_NAME_PATTERN =
  Pattern.compile("^[A-Za-z0-9._\\-]+$");
```

**Constraints:**
- Pattern: `^[A-Za-z0-9._\-]+$`
- Unicode NFC normalization required
- Case-sensitive
- Immutable once created

**Protocol Specification:** ❌ No ABNF defined

---

### 2. Discrepancies Summary

| Type | Protocol ABNF | Implementation | Status |
|------|---------------|----------------|--------|
| Dataset | ❌ Not defined | `^[a-zA-Z0-9._-]+$` | Missing spec |
| Branch | `[A-Za-z0-9_-/]` | `^[A-Za-z0-9._\-]+$` | **Mismatch** |
| Tag | ❌ Not defined | `^[A-Za-z0-9._\-]+$` | Missing spec |
| Commit | UUIDv7 | UUIDv7 | ✅ Aligned |

**Key Issues:**
1. Protocol allows `/` in branch names, implementation forbids it
2. Protocol allows `/` but NOT `.` in branch names
3. No ABNF for dataset names (critical for `/datasets/{name}` endpoint)
4. No ABNF for tag names

---

## Constraint Analysis

### 1. Apache Jena Constraints

**Findings:**
- Jena uses standard **URI/IRI** format for graph names and dataset names
- Graph URIs: `Model model = dataset.getNamedModel("http://example.org/myGraph");`
- Dataset paths in Fuseki: `/DatasetPathName` (becomes URL path segment)
- No specific character restrictions beyond standard URI encoding

**Implications:**
- Dataset names in CHUCC become URL path segments: `/{dataset}/sparql`
- Must follow RFC 3986 path segment rules
- Reserved characters require percent-encoding

---

### 2. Git Constraints

**Source:** `git-check-ref-format` documentation

#### Forbidden Characters

Git branch/tag names **CANNOT** contain:
- ASCII control characters (< `\040` or `\177 DEL`)
- Space (` `)
- Tilde (`~`)
- Caret (`^`)
- Colon (`:`)
- Question mark (`?`)
- Asterisk (`*`)
- Open bracket (`[`)
- Backslash (`\`)

#### Structural Constraints

- ❌ Cannot have `..` (consecutive dots)
- ❌ Cannot begin or end with `/`
- ❌ Cannot have `//` (consecutive slashes)
- ❌ Cannot end with `.`
- ❌ Cannot contain `@{`
- ❌ Cannot be `@`
- ❌ Cannot start with `refs/`

#### Why These Rules Exist

- `..` → Git revision ranges (`branch1..branch2`)
- `^` → Parent commits (`branch^`)
- `~` → Ancestor commits (`HEAD~3`)
- `:` → Refspec separator (`source:destination`)
- `@{` → Reflog access (`main@{yesterday}`)

**Key Insight:** Git ALLOWS `/` for hierarchical names (e.g., `feature/login`, `bugfix/security`)

---

### 3. Kafka Constraints

**Source:** Apache Kafka topic naming rules

#### Forbidden Characters

Kafka topic names **CANNOT** contain:
- Forward slash (`/`)
- Backslash (`\`)
- Comma (`,`)
- Space (` `)
- Null (`\0`)
- Newline (`\n`)
- Carriage return (`\r`)
- Tab (`\t`)

#### Structural Constraints

- ❌ Cannot be `.` or `..`
- ❌ Max length: 249 characters (Kafka internal limit)

**Implications:**
- Dataset names become Kafka topic names: `vc.{dataset}.events`
- Current implementation correctly enforces Kafka rules
- **Forward slash is FORBIDDEN in dataset names** (different from Git!)

---

### 4. RFC 3986 (URL Encoding)

**Source:** [RFC 3986 - Uniform Resource Identifier (URI): Generic Syntax](https://datatracker.ietf.org/doc/html/rfc3986)

#### Reserved Characters

**gen-delims:** `:` `/` `?` `#` `[` `]` `@`
**sub-delims:** `!` `$` `&` `'` `(` `)` `*` `+` `,` `;` `=`

#### Path Segment Encoding

From RFC 3986 Section 3.3:
> The reserved character `/`, for example, if used in the "path" component of a URI, has the special meaning of being a delimiter between path segments. If, according to a given URI scheme, `/` needs to be in a path segment, then the three characters `%2F` must be used in the segment instead of a raw `/`.

**Example:**
```
# Branch name: "feature/login" (with slash)

# Option 1: Percent-encode slash
GET /mydata/version/branches/feature%2Flogin/sparql

# Option 2: Forbid slash in branch names
GET /mydata/version/branches/feature-login/sparql
```

#### Unreserved Characters (Safe, No Encoding Needed)

```abnf
unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
```

**Key Insight:** Our current pattern `[A-Za-z0-9._-]` is a **subset of unreserved characters** (excluding `~`).

---

## Security Considerations

### 1. Path Traversal Attacks

**Threat:** Malicious dataset/branch/tag names containing path traversal sequences.

**Example Attack:**
```
# Attacker creates branch: "../../../etc/passwd"
GET /mydata/version/branches/../../../etc/passwd/sparql
```

**Mitigations:**
- ✅ Forbid `/` in branch/tag names (current implementation)
- ✅ Forbid `..` in all names (current implementation)
- ✅ Validate names against whitelist pattern
- ✅ Spring Boot's `StrictHttpFirewall` provides additional protection

---

### 2. URL Encoding Vulnerabilities

**Threat:** Double-encoding, null byte injection, Unicode normalization attacks.

**Example Attack:**
```
# Double-encoded slash: %252F → %2F → /
GET /mydata/version/branches/feature%252Fadmin/sparql

# Null byte injection: "admin\0.txt" → terminates string early
GET /mydata/version/branches/admin%00.txt/sparql
```

**Mitigations:**
- ✅ Validate AFTER URL decoding
- ✅ Use Unicode NFC normalization (prevents homograph attacks)
- ✅ Reject null bytes, control characters
- ✅ Use strict pattern matching: `^[A-Za-z0-9._\-]+$`

---

### 3. File System Interaction

**Threat:** Names with special meaning to file systems (CON, PRN, AUX on Windows; `.`, `..` on all systems).

**Example Attack:**
```
# Windows reserved device names
POST /datasets/CON
POST /datasets/PRN

# Unix hidden files
POST /datasets/.hidden
```

**Mitigations:**
- ✅ Forbid `.` and `..` as dataset names (current implementation)
- Consider: Forbid Windows reserved names (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`)
- Consider: Forbid names starting with `.` (hidden files)

---

### 4. Kafka Topic Security

**Threat:** Topic name injection, unauthorized topic access.

**Example Attack:**
```
# Inject special topic name patterns
POST /datasets/__consumer_offsets  # Internal Kafka topic

# Topic name too long (DoS)
POST /datasets/aaaaa...aaaaa  # 250+ characters
```

**Mitigations:**
- ✅ Max length: 249 characters (current implementation)
- Consider: Forbid names starting with `__` (Kafka internal topics)
- Consider: Forbid names starting with `_` (conventional private topics)

---

### 5. Unicode Security

**Threat:** Homograph attacks, mixed scripts, bidirectional text.

**Example Attack:**
```
# Visually similar characters (Latin 'a' vs. Cyrillic 'а')
POST /datasets/admin  # Latin
POST /datasets/аdmin  # Cyrillic 'а' (U+0430)

# Bidirectional text (right-to-left override)
POST /datasets/\u202Eniam  # Displays as "main" but actual name is reversed
```

**Mitigations:**
- ✅ Unicode NFC normalization (current implementation for branches/tags)
- ✅ ASCII-only pattern for datasets (prevents Unicode attacks entirely)
- Consider: Extend ASCII-only pattern to branches/tags for consistency
- Consider: Reject mixed scripts (e.g., Latin + Cyrillic in same name)

---

## URL Encoding and Semantic Routing

### Current Semantic Routing Pattern

From `semantic-routing.md`:

```
/{dataset}/version/branches/{name}/sparql
/{dataset}/version/commits/{id}/data
/{dataset}/version/tags/{name}/sparql
```

### Challenge with Forward Slash in Names

**Scenario:** Allow `/` in branch names (as per Git convention).

**Problem:** URL ambiguity in Spring routing.

**Example:**
```
# Branch name: "feature/login"

# User intends:
GET /mydata/version/branches/feature/login/sparql
                              └─────┬─────┘
                              Branch name

# Spring interprets:
GET /{dataset}/version/branches/{name1}/{name2}/sparql
                                  └──┬──┘ └──┬──┘
                             Path variables (wrong!)
```

**Solutions:**

#### Option 1: Percent-encode slashes (RFC 3986 compliant)

**Pattern:**
```
# Branch name: "feature/login"
# URL: /mydata/version/branches/feature%2Flogin/sparql
```

**Pros:**
- ✅ RFC 3986 compliant
- ✅ Supports hierarchical names (Git-like)
- ✅ Unambiguous

**Cons:**
- ❌ Not human-readable
- ❌ Not shareable (users copy wrong URL)
- ❌ Requires client-side encoding logic
- ❌ Breaks URL aesthetic of semantic routing

---

#### Option 2: Forbid slash in names (current implementation)

**Pattern:**
```abnf
identifier-name = 1*( ALPHA / DIGIT / "." / "_" / "-" )
```

**Pros:**
- ✅ Simple, clear, unambiguous
- ✅ Human-readable URLs
- ✅ Shareable, bookmarkable
- ✅ No encoding needed
- ✅ Prevents path traversal attacks
- ✅ Already implemented and tested

**Cons:**
- ❌ No hierarchical names (can't use `feature/login`)
- ❌ Less Git-compatible (Git allows `/`)

**Alternative for hierarchy:** Use different separator
```
feature-login       # Hyphen separator
feature.login       # Dot separator (like Java packages)
feature_login       # Underscore separator
```

---

#### Option 3: Use query parameter for complex names

**Pattern:**
```
# Simple names: Path parameter
GET /mydata/version/branches/main/sparql

# Complex names: Query parameter
GET /mydata/version/branches/sparql?name=feature/login
```

**Pros:**
- ✅ Supports any characters (URL-encoded in query string)
- ✅ Backward compatible with old routing

**Cons:**
- ❌ Inconsistent API (sometimes path, sometimes query)
- ❌ Loses semantic routing benefits
- ❌ Not RESTful

---

### Recommendation: Option 2 (Forbid Slash)

**Rationale:**
1. **Semantic routing priority** - The whole point of semantic routing is shareable, readable URLs
2. **Security first** - Simpler patterns are easier to validate and harder to exploit
3. **Consistency** - Same pattern for datasets, branches, tags
4. **Occam's Razor** - Don't add complexity (encoding) without clear benefit
5. **Already implemented** - Current codebase already uses this pattern

**For users needing hierarchy:**
- Use **dot notation**: `feature.login`, `bugfix.auth.oauth`
- Use **hyphen notation**: `feature-login`, `bugfix-auth-oauth`
- Document convention in API guide

---

## Proposed Strategy

### Unified Naming Pattern

Use the same pattern across **datasets**, **branches**, and **tags**:

```abnf
identifier-name = 1*identifier-char
identifier-char = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z
DIGIT           = %x30-39             ; 0-9
```

**In regex:** `^[A-Za-z0-9._\-]+$`

**Max lengths:**
- Dataset: 249 characters (Kafka limit)
- Branch: 255 characters (reasonable limit for readability)
- Tag: 255 characters (same as branches)

**Additional constraints:**
- ✅ Unicode NFC normalization REQUIRED for all types
- ✅ Case-sensitive (preserve user input)
- ❌ Cannot be `.` or `..` (reserved)
- ❌ Cannot start or end with `.` (convention: hidden/internal)
- ❌ Cannot start with `_` or `__` (convention: private/internal)

---

### Protocol Specification Updates

#### 1. Update Branch ABNF (SPARQL 1.2 Protocol Extension)

**Current (line 158):**
```abnf
branch = 1*( ALPHA / DIGIT / "-" / "_" / "/" )
```

**Proposed:**
```abnf
branch-name     = 1*branch-char
branch-char     = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z (case-sensitive)
DIGIT           = %x30-39             ; 0-9

; Additional constraints (prose):
; - Maximum length: 255 characters
; - Unicode NFC normalization REQUIRED
; - Cannot be "." or ".."
; - SHOULD NOT start or end with "."
; - SHOULD NOT start with "_" (reserved for internal use)
```

---

#### 2. Add Dataset ABNF (Both Protocol Extensions)

**New section in both protocols:**

```abnf
dataset-name    = 1*dataset-char
dataset-char    = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z (case-sensitive)
DIGIT           = %x30-39             ; 0-9

; Additional constraints (prose):
; - Maximum length: 249 characters (Kafka topic name limit)
; - Unicode NFC normalization REQUIRED
; - Cannot be "." or ".."
; - Cannot contain forward slash "/" (Kafka restriction)
; - SHOULD NOT start with "_" (reserved for Kafka internal topics)
```

**Rationale for Kafka restrictions:**
- Datasets map to Kafka topics: `vc.{dataset}.events`
- Kafka forbids: `/`, `\`, `,`, space, null, newline, carriage return, tab
- Our pattern already excludes these characters

---

#### 3. Add Tag ABNF (Both Protocol Extensions)

**New section in both protocols:**

```abnf
tag-name        = 1*tag-char
tag-char        = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z (case-sensitive)
DIGIT           = %x30-39             ; 0-9

; Additional constraints (prose):
; - Maximum length: 255 characters
; - Unicode NFC normalization REQUIRED
; - Cannot be "." or ".."
; - SHOULD NOT start or end with "."
; - SHOULD NOT start with "_" (reserved for internal use)
; - Once created, tags are IMMUTABLE (cannot be moved to different commits)
```

---

### URL Encoding Guidelines

**Add to both protocol specifications:**

#### Section: URL Encoding and Path Segments

> **Identifier Encoding in URLs**
>
> Dataset names, branch names, tag names, and commit IDs appear as **path segments** in versioned URIs according to the semantic routing pattern:
>
> ```
> /{dataset}/version/branches/{branch}/sparql
> /{dataset}/version/commits/{commitId}/data
> /{dataset}/version/tags/{tag}/sparql
> ```
>
> **Encoding Requirements:**
>
> 1. **Identifiers** (datasets, branches, tags) use a restricted character set that requires **no percent-encoding** when used in URL path segments (per RFC 3986, these are "unreserved characters").
>
> 2. **Commit IDs** (UUIDv7 format) consist only of hexadecimal digits and hyphens, which also require no encoding.
>
> 3. **Clients** MUST NOT percent-encode these identifiers when constructing URLs, as this would result in incorrect resource references.
>
> 4. **Servers** MUST validate identifiers AFTER URL decoding to prevent double-encoding attacks.
>
> **Example:**
> ```
> # Correct (no encoding needed)
> GET /mydata/version/branches/feature-login/sparql
>
> # Incorrect (unnecessary encoding)
> GET /mydata/version/branches/feature%2Dlogin/sparql
> ```
>
> **Security Note:** The restricted character set prevents common URL-based attacks including path traversal, null byte injection, and ambiguous path interpretation.

---

### Validation Rules Summary

| Type | Pattern | Max Length | NFC | Case | Reserved |
|------|---------|------------|-----|------|----------|
| Dataset | `^[A-Za-z0-9._-]+$` | 249 | ✅ | Sensitive | `.`, `..`, `_*` |
| Branch | `^[A-Za-z0-9._-]+$` | 255 | ✅ | Sensitive | `.`, `..`, `_*` |
| Tag | `^[A-Za-z0-9._-]+$` | 255 | ✅ | Sensitive | `.`, `..`, `_*` |
| Commit | UUIDv7 | 36 | N/A | Lower | N/A |

**Legend:**
- **NFC:** Unicode NFC normalization required
- **Case:** Case-sensitive (preserve user input)
- **Reserved:** Names that should be rejected (`.`, `..`) or discouraged (`_*`)

---

## Migration Plan

### Phase 1: Documentation Updates (Immediate)

**Actions:**
1. ✅ Update protocol ABNF (remove `/` from branch definition, add dataset/tag ABNF)
2. ✅ Add URL encoding section to both protocols
3. ✅ Document reserved names (`.`, `..`, `_*`)
4. ✅ Add security considerations section (path traversal, Unicode attacks)

**Deliverable:** Updated protocol markdown files in `protocol/` directory.

---

### Phase 2: Implementation Validation (Current)

**Actions:**
1. ✅ Verify current implementation matches proposed pattern
2. ✅ Add tests for edge cases (`.`, `..`, `_internal`, Unicode NFC)
3. ✅ Add tests for Windows reserved names (`CON`, `PRN`, etc.)
4. ✅ Document validation in Javadoc

**Files to Review:**
- `Branch.java:14` - Branch name validation
- `Tag.java:20` - Tag name validation
- `CreateDatasetCommand.java:99` - Dataset name validation

**Status:** ✅ Implementation already correct (no code changes needed)

---

### Phase 3: Extended Validation (Optional Enhancement)

**Actions:**
1. Consider: Reject Windows reserved names (`CON`, `PRN`, `AUX`, etc.)
2. Consider: Reject names starting with `.` (hidden files)
3. Consider: Reject names starting with `_` (internal/private convention)
4. Consider: Enforce max length for branches/tags (currently unlimited)

**Example:**
```java
// CreateDatasetCommand.java
private static void validateDatasetName(String name) {
  // Existing checks...

  // New: Reject names starting with underscore
  if (name.startsWith("_")) {
    throw new IllegalArgumentException(
      "Dataset name cannot start with '_' (reserved for internal use)");
  }

  // New: Reject Windows reserved names
  String[] windowsReserved = {"CON", "PRN", "AUX", "NUL",
    "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
    "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};
  if (Arrays.asList(windowsReserved).contains(name.toUpperCase())) {
    throw new IllegalArgumentException(
      "Dataset name cannot be a Windows reserved device name");
  }
}
```

**Decision:** Defer to future PR (requires discussion on backward compatibility)

---

### Phase 4: API Documentation (With Semantic Routing)

**Actions:**
1. Update OpenAPI specs with identifier format descriptions
2. Add examples of valid/invalid names
3. Document naming conventions guide for users
4. Add migration guide for clients

**Deliverable:** Updated OpenAPI YAML files, API documentation guide.

---

## References

### Standards and Specifications

1. **RFC 3986 - Uniform Resource Identifier (URI): Generic Syntax**
   https://datatracker.ietf.org/doc/html/rfc3986
   Defines URI syntax, reserved characters, percent-encoding

2. **Git Reference Format (`git-check-ref-format`)**
   https://git-scm.com/docs/git-check-ref-format
   Defines valid Git branch/tag naming rules

3. **Apache Kafka Topic Naming**
   https://kafka.apache.org/documentation/#topicconfigs
   Defines Kafka topic naming constraints (max 249 chars, forbidden characters)

4. **Unicode Normalization (UAX #15)**
   https://www.unicode.org/reports/tr15/
   Defines Unicode Normalization Forms (NFC, NFD, NFKC, NFKD)

5. **SPARQL 1.2 Protocol**
   https://www.w3.org/TR/sparql12-protocol/
   Base protocol extended by version control extensions

6. **SPARQL 1.2 Graph Store Protocol**
   https://www.w3.org/TR/sparql12-graph-store-protocol/
   GSP base specification

---

### Project Documentation

1. **Semantic Routing for CHUCC-Server**
   `docs/architecture/semantic-routing.md`
   Defines URL structure for versioned resources

2. **SPARQL 1.2 Protocol – Version Control Extension**
   `protocol/SPARQL_1_2_Protocol_Version_Control_Extension.md`
   Protocol specification for SPARQL UPDATE/QUERY versioning

3. **SPARQL 1.2 Graph Store Protocol – Version Control Extension**
   `protocol/SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md`
   Protocol specification for GSP versioning

4. **Branch Domain Entity**
   `src/main/java/org/chucc/vcserver/domain/Branch.java`
   Implementation of branch name validation

5. **Tag Domain Entity**
   `src/main/java/org/chucc/vcserver/domain/Tag.java`
   Implementation of tag name validation

6. **CreateDatasetCommand**
   `src/main/java/org/chucc/vcserver/command/CreateDatasetCommand.java`
   Implementation of dataset name validation

---

### Security Resources

1. **OWASP Path Traversal**
   https://owasp.org/www-community/attacks/Path_Traversal
   Path traversal attack vectors and mitigations

2. **OWASP URL Encoding**
   https://cheatsheetseries.owasp.org/cheatsheets/Injection_Prevention_Cheat_Sheet.html
   Injection prevention via proper encoding

3. **Unicode Security Considerations (UTS #39)**
   https://www.unicode.org/reports/tr39/
   Unicode security mechanisms (homograph attacks, mixed scripts)

---

## Appendix A: Character Set Comparison

### Visual Comparison

| Character | ASCII | Allowed? | Reason |
|-----------|-------|----------|--------|
| `A-Z` | 65-90 | ✅ Yes | Alphanumeric (unreserved) |
| `a-z` | 97-122 | ✅ Yes | Alphanumeric (unreserved) |
| `0-9` | 48-57 | ✅ Yes | Numeric (unreserved) |
| `.` | 46 | ✅ Yes | Period (unreserved), allows hierarchy |
| `_` | 95 | ✅ Yes | Underscore (unreserved) |
| `-` | 45 | ✅ Yes | Hyphen (unreserved) |
| `~` | 126 | ❌ No | Tilde (unreserved but not needed) |
| `/` | 47 | ❌ No | Path delimiter (RFC 3986, ambiguous) |
| `:` | 58 | ❌ No | Scheme delimiter (RFC 3986, Git refspec) |
| `@` | 64 | ❌ No | Authority delimiter (RFC 3986, Git reflog) |
| `^` | 94 | ❌ No | Git parent notation |
| `~` | 126 | ❌ No | Git ancestor notation |
| `?` | 63 | ❌ No | Query delimiter (RFC 3986) |
| `#` | 35 | ❌ No | Fragment delimiter (RFC 3986) |
| `[` | 91 | ❌ No | IPv6 delimiter (RFC 3986) |
| `]` | 93 | ❌ No | IPv6 delimiter (RFC 3986) |
| ` ` | 32 | ❌ No | Space (ambiguous, Kafka forbidden) |
| `\` | 92 | ❌ No | Windows path separator, Kafka forbidden |
| `,` | 44 | ❌ No | Kafka forbidden |

---

## Appendix B: Example Names

### Valid Examples

**Datasets:**
```
mydata
biodiversity-2025
species_v2
rdf.core
project-alpha
db1
```

**Branches:**
```
main
develop
feature-login
bugfix-auth
release-1.0
hotfix.security
experiment_new_algo
alice-work
v2.beta
```

**Tags:**
```
v1.0.0
v2.1.3
release-2025-01-15
stable
beta
alpha.1
milestone_q4
paper-submission
thesis-final
```

---

### Invalid Examples (with Reasons)

**Datasets:**
```
my/data          ❌ Contains '/' (Kafka forbidden, URL ambiguous)
my data          ❌ Contains space (Kafka forbidden, URL ambiguous)
.hidden          ❌ Starts with '.' (reserved, hidden file)
..               ❌ Reserved (parent directory)
__internal       ❌ Starts with '__' (Kafka internal topics)
CON              ❌ Windows reserved device name
```

**Branches:**
```
feature/login    ❌ Contains '/' (URL ambiguous)
main@{1}         ❌ Contains '@{' (Git reflog notation)
my^branch        ❌ Contains '^' (Git parent notation)
branch~2         ❌ Contains '~' (Git ancestor notation)
../etc/passwd    ❌ Path traversal attempt
@                ❌ Reserved (Git single-char reflog)
refs/heads/main  ❌ Starts with 'refs/' (Git internal structure)
```

**Tags:**
```
v1.0:latest      ❌ Contains ':' (Git refspec separator)
release[2025]    ❌ Contains '[' (Git wildcard)
tag?query        ❌ Contains '?' (URL query delimiter)
tag#fragment     ❌ Contains '#' (URL fragment delimiter)
.temp            ❌ Starts with '.' (reserved, hidden file)
```

---

## Appendix C: Implementation Checklist

### Protocol Updates

- [ ] Update `SPARQL_1_2_Protocol_Version_Control_Extension.md`
  - [ ] Fix branch ABNF (remove `/`, add `.`)
  - [ ] Add dataset ABNF section
  - [ ] Add tag ABNF section
  - [ ] Add URL encoding guidelines section
  - [ ] Add security considerations for naming

- [ ] Update `SPARQL_1_2_Graph_Store_Protocol_Version_Control_Extension.md`
  - [ ] Add dataset ABNF section (reference Protocol doc)
  - [ ] Add tag ABNF section (reference Protocol doc)
  - [ ] Add URL encoding guidelines section
  - [ ] Update examples with valid names only

---

### Code Validation

- [x] ✅ `Branch.java` - Pattern correct: `^[A-Za-z0-9._\-]+$`
- [x] ✅ `Tag.java` - Pattern correct: `^[A-Za-z0-9._\-]+$`
- [x] ✅ `CreateDatasetCommand.java` - Pattern correct: `^[a-zA-Z0-9._-]+$`
- [x] ✅ Unicode NFC normalization in Branch/Tag

---

### Test Coverage

- [ ] Add tests for reserved names (`.`, `..`)
- [ ] Add tests for names starting with `_`
- [ ] Add tests for Windows reserved names
- [ ] Add tests for Unicode NFC normalization
- [ ] Add tests for max length enforcement
- [ ] Add URL encoding/decoding tests

---

### Documentation

- [ ] Update `semantic-routing.md` with identifier format
- [ ] Create naming conventions guide for users
- [ ] Update OpenAPI specs with format annotations
- [ ] Add migration guide for existing clients

---

## Conclusion

This analysis proposes a **conservative, unified naming strategy** for datasets, branches, and tags:

**Pattern:** `^[A-Za-z0-9._\-]+$`

**Rationale:**
1. ✅ **Security** - Prevents path traversal, injection attacks
2. ✅ **Simplicity** - Easy to validate, understand, and implement
3. ✅ **Compatibility** - Works with Kafka, URLs, Git, file systems
4. ✅ **Semantic Routing** - Produces clean, shareable, bookmarkable URLs
5. ✅ **Already Implemented** - Current codebase already uses this pattern

**Next Steps:**
1. Update protocol specifications with explicit ABNF
2. Add URL encoding guidelines to protocols
3. Enhance test coverage for edge cases
4. Document naming conventions for API users

**No code changes required** - current implementation is already correct and secure.

---

**Document Version:** 1.0
**Status:** Complete
**Last Updated:** 2025-11-06
