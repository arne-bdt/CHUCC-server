# Naming Conventions for SPARQL Version Control Extensions

**Status:** Normative
**Version:** 1.0
**Date:** 2025-11-06

---

## Quick Reference

### Unified Pattern (All Identifiers)

```abnf
identifier-name = 1*identifier-char
identifier-char = ALPHA / DIGIT / "." / "_" / "-"
ALPHA           = %x41-5A / %x61-7A   ; A-Z / a-z (case-sensitive)
DIGIT           = %x30-39             ; 0-9
```

**Regex:** `^[A-Za-z0-9._\-]+$`

---

## Type-Specific Constraints

### Dataset Names

```abnf
dataset-name = 1*249identifier-char
```

**Constraints:**
- Maximum length: **249 characters** (Kafka topic limit)
- Unicode NFC normalization REQUIRED
- Cannot be `.` or `..`
- SHOULD NOT start with `_` (reserved for internal Kafka topics)

**Rationale:** Dataset names become Kafka topics (`vc.{dataset}.events`)

**Examples:**
```
✅ mydata
✅ biodiversity-2025
✅ species_v2
✅ rdf.core
❌ my/data         (contains /)
❌ my data         (contains space)
❌ __internal      (starts with __)
❌ .hidden         (starts with .)
```

---

### Branch Names

```abnf
branch-name = 1*255identifier-char
```

**Constraints:**
- Maximum length: **255 characters** (recommended)
- Unicode NFC normalization REQUIRED
- Cannot be `.` or `..`
- SHOULD NOT start or end with `.`
- SHOULD NOT start with `_` (reserved for internal use)

**Examples:**
```
✅ main
✅ develop
✅ feature-login
✅ bugfix.auth
✅ release_1.0
❌ feature/login   (contains /)
❌ main@{1}        (contains @{)
❌ ../etc/passwd   (path traversal)
❌ refs/heads/main (starts with refs/)
```

---

### Tag Names

```abnf
tag-name = 1*255identifier-char
```

**Constraints:**
- Maximum length: **255 characters** (recommended)
- Unicode NFC normalization REQUIRED
- Cannot be `.` or `..`
- SHOULD NOT start or end with `.`
- SHOULD NOT start with `_` (reserved for internal use)
- Tags are **immutable** once created

**Examples:**
```
✅ v1.0.0
✅ release-2025-01-15
✅ stable
✅ beta.1
❌ v1.0:latest     (contains :)
❌ release[2025]   (contains [)
❌ .temp           (starts with .)
```

---

### Commit IDs

```abnf
commit-id = 8HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 4HEXDIG "-" 12HEXDIG
          ; UUIDv7 format (lowercase hexadecimal)
          ; Version nibble MUST be 7
```

**Examples:**
```
✅ 01936d8f-1234-7890-abcd-ef1234567890
❌ 01936d8f-1234-4890-abcd-ef1234567890  (version nibble = 4, not 7)
```

---

## URL Encoding Guidelines

### Semantic Routing Pattern

```
/{dataset}/version/branches/{branch}/sparql
/{dataset}/version/commits/{commitId}/data
/{dataset}/version/tags/{tag}/sparql
```

### Encoding Requirements

**No encoding needed** - The restricted character set (`[A-Za-z0-9._-]`) consists entirely of **unreserved characters** per RFC 3986.

**Clients MUST NOT percent-encode identifiers:**
```
✅ GET /mydata/version/branches/feature-login/sparql
❌ GET /mydata/version/branches/feature%2Dlogin/sparql
```

**Servers MUST validate identifiers AFTER URL decoding** to prevent double-encoding attacks.

---

## Security Considerations

### Path Traversal Prevention

Forbidden characters prevent path traversal attacks:
- ❌ No forward slash `/` (path delimiter)
- ❌ No backslash `\` (Windows path separator)
- ❌ No `..` (parent directory reference)

### URL Injection Prevention

Forbidden characters prevent URL injection:
- ❌ No `?` (query delimiter)
- ❌ No `#` (fragment delimiter)
- ❌ No `@` (authority delimiter)
- ❌ No `:` (scheme/port delimiter)

### Unicode Security

- ✅ Unicode NFC normalization prevents homograph attacks
- ✅ Case-sensitive comparison (preserve user input)
- Consider: ASCII-only for maximum security

### Kafka Topic Security

- ❌ No `_` prefix (Kafka internal topics)
- ❌ Max 249 characters (Kafka limit)
- ❌ No special Kafka characters (`/`, `\`, `,`, space, null, newline, tab)

---

## Why These Rules?

### 1. Forward Slash (`/`) is Forbidden

**Problem:** URL ambiguity in semantic routing

```
# Branch name: "feature/login"
GET /mydata/version/branches/feature/login/sparql
                             └─────┬─────┘
                          Ambiguous: one path segment or two?
```

**Solution:** Forbid `/`, use alternative separators:
- `feature-login` (hyphen)
- `feature.login` (dot, like Java packages)
- `feature_login` (underscore)

### 2. Special Characters are Forbidden

**Git notation conflicts:**
- `^` → Parent commits (`HEAD^`)
- `~` → Ancestors (`HEAD~3`)
- `@{` → Reflog (`main@{yesterday}`)
- `:` → Refspec separator (`source:destination`)

**URL delimiter conflicts:**
- `?` → Query string delimiter
- `#` → Fragment delimiter
- `@` → Authority delimiter
- `:` → Scheme/port delimiter

### 3. Unicode NFC Normalization

**Prevents homograph attacks:**
```
# Latin 'a' vs. Cyrillic 'а' (U+0430)
admin  ≠  аdmin
```

**Implementation:**
```java
String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
if (!normalized.equals(name)) {
  throw new IllegalArgumentException("Name must be in NFC form");
}
```

---

## Migration from Protocol ABNF

### Previous Branch Definition (Incorrect)

```abnf
branch = 1*( ALPHA / DIGIT / "-" / "_" / "/" )
```

**Problem:** Allowed `/` (forward slash), which breaks semantic routing.

### Current Definition (Correct)

```abnf
branch-name = 1*( ALPHA / DIGIT / "." / "_" / "-" )
```

**Changes:**
- ❌ Removed: `/` (forward slash)
- ✅ Added: `.` (period) for hierarchical names

---

## Compliance

### Apache Jena

✅ Compatible - Uses standard URI format, no specific restrictions

### Git

✅ Subset of Git rules - More restrictive (safer)
- Git allows: `/`, `~`, `^`, `:`, `?`, `*`, `[`
- CHUCC forbids: All of the above
- Benefit: Simpler, more secure

### Kafka

✅ Fully compatible with Kafka topic names
- Kafka forbids: `/`, `\`, `,`, space, null, newline, tab
- CHUCC forbids: Same characters (and more)

### RFC 3986 (URLs)

✅ Optimal - Uses only unreserved characters
- No percent-encoding needed
- Human-readable, shareable URLs

---

## For API Users

### Creating Valid Names

**Do:**
```bash
# Datasets
POST /datasets/biodiversity-2025
POST /datasets/rdf.core

# Branches
POST /mydata/version/branches -d '{"name":"feature-login","from":"main"}'
POST /mydata/version/branches -d '{"name":"release.v2","from":"develop"}'

# Tags
POST /mydata/version/tags -d '{"name":"v1.0.0","target":"01936d8f..."}'
POST /mydata/version/tags -d '{"name":"stable.2025-01","target":"..."}'
```

**Don't:**
```bash
# ❌ Forward slash breaks routing
POST /datasets/my/data
POST /mydata/version/branches -d '{"name":"feature/login","from":"main"}'

# ❌ Special characters break Git/URL semantics
POST /mydata/version/tags -d '{"name":"v1.0:latest","target":"..."}'
POST /mydata/version/tags -d '{"name":"release@{1}","target":"..."}'

# ❌ Reserved names
POST /datasets/.
POST /datasets/__internal
POST /mydata/version/branches -d '{"name":"refs/heads/main","from":"main"}'
```

### Hierarchical Naming

Use **dot notation** for hierarchy (like Java packages):

```
# Feature branches
feature.authentication
feature.authentication.oauth
feature.authentication.oauth.google

# Bug fixes
bugfix.security
bugfix.security.xss
bugfix.performance.query

# Releases
release.v2
release.v2.1
release.v2.1.3
```

**Alternative:** Use hyphen notation:
```
feature-authentication-oauth-google
bugfix-security-xss-patch
release-v2-1-3
```

---

## For Implementers

### Validation Function

```java
public static void validateIdentifierName(String name, int maxLength, String type) {
  Objects.requireNonNull(name, type + " name cannot be null");

  if (name.isBlank()) {
    throw new IllegalArgumentException(type + " name cannot be blank");
  }

  if (name.length() > maxLength) {
    throw new IllegalArgumentException(
      type + " name too long (max " + maxLength + " characters)");
  }

  // Normalize to NFC
  String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
  if (!normalized.equals(name)) {
    throw new IllegalArgumentException(
      type + " name must be in Unicode NFC normalization form");
  }

  // Validate pattern
  if (!name.matches("^[A-Za-z0-9._\\-]+$")) {
    throw new IllegalArgumentException(
      type + " name contains invalid characters. " +
      "Allowed: A-Z, a-z, 0-9, . (period), _ (underscore), - (hyphen)");
  }

  // Reserved names
  if (name.equals(".") || name.equals("..")) {
    throw new IllegalArgumentException(
      type + " name cannot be '.' or '..'");
  }

  // Optional: Reject names starting with underscore
  if (name.startsWith("_")) {
    throw new IllegalArgumentException(
      type + " name cannot start with '_' (reserved for internal use)");
  }
}

// Usage:
validateIdentifierName(dataset, 249, "Dataset");
validateIdentifierName(branch, 255, "Branch");
validateIdentifierName(tag, 255, "Tag");
```

---

## References

- **Full Analysis:** `docs/architecture/naming-conventions-analysis.md`
- **RFC 3986:** Uniform Resource Identifier (URI) syntax
- **Git:** `git-check-ref-format` documentation
- **Kafka:** Topic naming guidelines
- **Unicode:** UAX #15 (Normalization Forms)

---

**Document Version:** 1.0
**Last Updated:** 2025-11-06
**Maintained By:** CHUCC Project Team
