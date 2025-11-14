# Task 8: Security Controls

**Phase:** 4 (Remote Endpoint Support)
**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Dependencies:** Task 7 (Remote Endpoint Client)

---

## Overview

This task implements security controls for remote endpoint access, preventing SSRF attacks and other security vulnerabilities.

**Features:**
- SSRF prevention (block private IP ranges, localhost)
- URL scheme validation (allow only http/https)
- Response size limits (already implemented in Task 7)
- Timeout enforcement (already implemented in Task 7)
- TLS certificate verification (optional)

**Security Threats Mitigated:**
- **SSRF (Server-Side Request Forgery)** - Attacker uses your server to scan internal network
- **Data exfiltration** - Fetch sensitive data from internal services
- **Denial of Service** - Trigger long-running queries against slow endpoints

**Goal:** Secure remote endpoint integration against common attack vectors.

---

## Current State

**Existing (from Task 7):**
- ✅ RemoteEndpointClient with timeout and size limits
- ✅ Remote endpoint configuration

**Missing:**
- ❌ URL validation (scheme and IP filtering)
- ❌ Private IP range blocking
- ❌ Localhost blocking
- ❌ Security-focused integration tests

---

## Requirements

### Blocked IP Ranges

**Private IPv4 ranges (RFC 1918):**
- 10.0.0.0/8 (10.0.0.0 - 10.255.255.255)
- 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
- 192.168.0.0/16 (192.168.0.0 - 192.168.255.255)

**Loopback:**
- 127.0.0.0/8 (127.0.0.1 - 127.255.255.255)
- ::1 (IPv6 loopback)

**Link-local:**
- 169.254.0.0/16 (169.254.0.0 - 169.254.255.255)

**Allowed schemes:**
- http, https

---

## Implementation Steps

### Step 1: Create URL Security Validator

**File:** `src/main/java/org/chucc/vcserver/security/UrlSecurityValidator.java`

```java
package org.chucc.vcserver.security;

import org.chucc.vcserver.exception.SecurityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validator for URL security checks (SSRF prevention).
 *
 * <p>Blocks access to:</p>
 * <ul>
 *   <li>Private IP ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)</li>
 *   <li>Loopback addresses (127.0.0.0/8, ::1)</li>
 *   <li>Link-local addresses (169.254.0.0/16)</li>
 *   <li>Non-HTTP/HTTPS schemes</li>
 * </ul>
 */
@Component
public class UrlSecurityValidator {

  private static final Logger logger = LoggerFactory.getLogger(UrlSecurityValidator.class);

  /**
   * Validate URL for security issues (SSRF prevention).
   *
   * @param url URL to validate
   * @throws SecurityViolationException if URL is blocked
   */
  public void validateUrl(String url) {
    try {
      URI uri = new URI(url);

      // Validate scheme
      validateScheme(uri);

      // Validate host (IP blocking)
      validateHost(uri);

    } catch (Exception e) {
      if (e instanceof SecurityViolationException) {
        throw (SecurityViolationException) e;
      }
      throw new SecurityViolationException("Invalid URL: " + e.getMessage());
    }
  }

  /**
   * Validate URI scheme (allow only http/https).
   *
   * @param uri URI to validate
   * @throws SecurityViolationException if scheme is blocked
   */
  private void validateScheme(URI uri) {
    String scheme = uri.getScheme();

    if (scheme == null) {
      throw new SecurityViolationException("URL scheme is required");
    }

    if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
      throw new SecurityViolationException(
          "URL scheme '" + scheme + "' is not allowed (only http/https permitted)"
      );
    }
  }

  /**
   * Validate URI host (block private IPs, localhost, etc.).
   *
   * @param uri URI to validate
   * @throws SecurityViolationException if host is blocked
   */
  private void validateHost(URI uri) {
    String host = uri.getHost();

    if (host == null || host.isBlank()) {
      throw new SecurityViolationException("URL host is required");
    }

    // Block localhost by name
    if ("localhost".equalsIgnoreCase(host)) {
      throw new SecurityViolationException(
          "Access to localhost is not allowed (SSRF prevention)"
      );
    }

    // Resolve hostname to IP address
    InetAddress address;
    try {
      address = InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new SecurityViolationException("Cannot resolve hostname: " + host);
    }

    // Check if IP is blocked
    validateIpAddress(address, host);
  }

  /**
   * Validate IP address (block private ranges, loopback, link-local).
   *
   * @param address IP address
   * @param host original hostname (for error messages)
   * @throws SecurityViolationException if IP is blocked
   */
  private void validateIpAddress(InetAddress address, String host) {
    byte[] ip = address.getAddress();

    // Check loopback (127.0.0.0/8 or ::1)
    if (address.isLoopbackAddress()) {
      throw new SecurityViolationException(
          "Access to loopback address is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // Check link-local (169.254.0.0/16 or fe80::/10)
    if (address.isLinkLocalAddress()) {
      throw new SecurityViolationException(
          "Access to link-local address is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // Check site-local (deprecated, but still check)
    if (address.isSiteLocalAddress()) {
      throw new SecurityViolationException(
          "Access to site-local address is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // IPv4-specific checks
    if (ip.length == 4) {
      validateIpv4Address(ip, host, address);
    }
  }

  /**
   * Validate IPv4 address (block private ranges).
   *
   * @param ip IP address bytes
   * @param host original hostname
   * @param address InetAddress object
   * @throws SecurityViolationException if IP is in blocked range
   */
  private void validateIpv4Address(byte[] ip, String host, InetAddress address) {
    int first = ip[0] & 0xFF;
    int second = ip[1] & 0xFF;

    // 10.0.0.0/8
    if (first == 10) {
      throw new SecurityViolationException(
          "Access to private IP range 10.0.0.0/8 is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // 172.16.0.0/12
    if (first == 172 && (second >= 16 && second <= 31)) {
      throw new SecurityViolationException(
          "Access to private IP range 172.16.0.0/12 is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // 192.168.0.0/16
    if (first == 192 && second == 168) {
      throw new SecurityViolationException(
          "Access to private IP range 192.168.0.0/16 is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    // 169.254.0.0/16 (link-local, should be caught by isLinkLocalAddress but double-check)
    if (first == 169 && second == 254) {
      throw new SecurityViolationException(
          "Access to link-local address 169.254.0.0/16 is not allowed (SSRF prevention): " + host +
          " (" + address.getHostAddress() + ")"
      );
    }

    logger.debug("URL validation passed: {} ({})", host, address.getHostAddress());
  }
}
```

### Step 2: Create SecurityViolationException

**File:** `src/main/java/org/chucc/vcserver/exception/SecurityViolationException.java`

```java
package org.chucc.vcserver.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a security violation is detected.
 *
 * <p>Common causes:</p>
 * <ul>
 *   <li>SSRF attempt (private IP, localhost)</li>
 *   <li>Invalid URL scheme</li>
 *   <li>Blocked hostname</li>
 * </ul>
 */
public class SecurityViolationException extends VcServerException {

  /**
   * Construct exception with message.
   *
   * @param message error message
   */
  public SecurityViolationException(String message) {
    super(message, HttpStatus.FORBIDDEN, "security_violation");
  }
}
```

### Step 3: Update RemoteEndpointClient

**File:** `src/main/java/org/chucc/vcserver/service/RemoteEndpointClient.java`

Add URL validation before fetching:

```java
// Add to class fields:
@Autowired
private UrlSecurityValidator urlSecurityValidator;

/**
 * Fetch graph from remote SPARQL endpoint.
 */
public Graph fetchGraph(String endpoint, String graphUri) {
  // Validate URL security BEFORE making request
  urlSecurityValidator.validateUrl(endpoint);

  String cacheKey = endpoint + ":" + graphUri;

  // ... rest of existing code
}
```

Add import:
```java
import org.chucc.vcserver.security.UrlSecurityValidator;
```

---

## Testing Strategy

### Integration Tests

**File:** `src/test/java/org/chucc/vcserver/integration/SecurityControlsIT.java`

```java
package org.chucc.vcserver.integration;

import org.chucc.vcserver.dto.shacl.DataReference;
import org.chucc.vcserver.dto.shacl.GraphReference;
import org.chucc.vcserver.dto.shacl.ValidationRequest;
import org.chucc.vcserver.testutil.ITFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SHACL validation security controls.
 *
 * <p>Tests SSRF prevention and URL validation.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class SecurityControlsIT extends ITFixture {

  private static final String SHAPES = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .

      ex:PersonShape a sh:NodeShape ;
          sh:targetClass ex:Person ;
          sh:property [
              sh:path ex:name ;
              sh:datatype <http://www.w3.org/2001/XMLSchema#string> ;
              sh:minCount 1 ;
          ] .
      """;

  @Test
  void validate_withLocalhostEndpoint_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://localhost:8080/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("localhost");
  }

  @Test
  void validate_withLoopbackIpEndpoint_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://127.0.0.1:8080/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("loopback");
  }

  @Test
  void validate_withPrivateIp10_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://10.0.0.1/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("10.0.0.0/8");
  }

  @Test
  void validate_withPrivateIp192_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://192.168.1.1/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("192.168.0.0/16");
  }

  @Test
  void validate_withPrivateIp172_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://172.16.0.1/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("172.16.0.0/12");
  }

  @Test
  void validate_withLinkLocalIp_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "http://169.254.1.1/sparql", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation");
  }

  @Test
  void validate_withFileScheme_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "file:///etc/passwd", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("file");
  }

  @Test
  void validate_withFtpScheme_shouldReturn403() {
    // Arrange
    ValidationRequest request = new ValidationRequest(
        new GraphReference("remote", null, null, "http://example.org/shapes",
            "ftp://example.org/shapes", null, null, null, null),
        new DataReference("local", getDatasetName(), List.of("default"), null, null, null, null),
        null,
        null
    );

    HttpEntity<ValidationRequest> httpEntity = new HttpEntity<>(request);

    // Act
    ResponseEntity<String> response = restTemplate.exchange(
        "/" + getDatasetName() + "/shacl",
        HttpMethod.POST,
        httpEntity,
        String.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody())
        .contains("security_violation")
        .contains("ftp");
  }
}
```

### Unit Tests

**File:** `src/test/java/org/chucc/vcserver/security/UrlSecurityValidatorTest.java`

```java
package org.chucc.vcserver.security;

import org.chucc.vcserver.exception.SecurityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UrlSecurityValidator.
 */
class UrlSecurityValidatorTest {

  private UrlSecurityValidator validator;

  @BeforeEach
  void setUp() {
    validator = new UrlSecurityValidator();
  }

  @Test
  void validateUrl_withPublicHttpsUrl_shouldPass() {
    // Should not throw
    validator.validateUrl("https://example.org/sparql");
  }

  @Test
  void validateUrl_withPublicHttpUrl_shouldPass() {
    // Should not throw
    validator.validateUrl("http://example.org/sparql");
  }

  @Test
  void validateUrl_withLocalhost_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://localhost:8080/sparql"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("localhost");
  }

  @Test
  void validateUrl_withLoopbackIp_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://127.0.0.1/sparql"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("loopback");
  }

  @Test
  void validateUrl_withPrivateIp10_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://10.0.0.1/sparql"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("10.0.0.0/8");
  }

  @Test
  void validateUrl_withPrivateIp192_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://192.168.1.1/sparql"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("192.168.0.0/16");
  }

  @Test
  void validateUrl_withPrivateIp172_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://172.16.0.1/sparql"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("172.16.0.0/12");
  }

  @Test
  void validateUrl_withLinkLocalIp_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("http://169.254.1.1/sparql"))
        .isInstanceOf(SecurityViolationException.class);
  }

  @Test
  void validateUrl_withFileScheme_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("file:///etc/passwd"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("file");
  }

  @Test
  void validateUrl_withFtpScheme_shouldThrow() {
    assertThatThrownBy(() -> validator.validateUrl("ftp://example.org/file"))
        .isInstanceOf(SecurityViolationException.class)
        .hasMessageContaining("ftp");
  }
}
```

### Test Scenarios

**Integration Tests (8 tests):**
1. Localhost → 403 security_violation
2. Loopback IP (127.0.0.1) → 403 security_violation
3. Private IP 10.0.0.0/8 → 403 security_violation
4. Private IP 192.168.0.0/16 → 403 security_violation
5. Private IP 172.16.0.0/12 → 403 security_violation
6. Link-local IP 169.254.0.0/16 → 403 security_violation
7. File scheme → 403 security_violation
8. FTP scheme → 403 security_violation

**Unit Tests (9 tests):**
- Same scenarios as integration tests
- Plus: Valid public URLs (http/https) → pass

**Test Pattern:** Projector DISABLED (security validation happens before any data operations)

---

## Files to Create

1. `src/main/java/org/chucc/vcserver/security/UrlSecurityValidator.java`
2. `src/main/java/org/chucc/vcserver/exception/SecurityViolationException.java`
3. `src/test/java/org/chucc/vcserver/integration/SecurityControlsIT.java`
4. `src/test/java/org/chucc/vcserver/security/UrlSecurityValidatorTest.java`

**Total Files:** 4 new files

---

## Files to Modify

1. `src/main/java/org/chucc/vcserver/service/RemoteEndpointClient.java` - Add URL validation

**Total Files:** 1 modified file

---

## Success Criteria

- ✅ Localhost blocked (by hostname)
- ✅ Loopback IP blocked (127.0.0.0/8)
- ✅ Private IP 10.0.0.0/8 blocked
- ✅ Private IP 192.168.0.0/16 blocked
- ✅ Private IP 172.16.0.0/12 blocked
- ✅ Link-local IP 169.254.0.0/16 blocked
- ✅ File scheme blocked
- ✅ FTP scheme blocked
- ✅ HTTP/HTTPS allowed for public IPs
- ✅ 8 integration tests passing
- ✅ 9 unit tests passing
- ✅ Zero Checkstyle violations
- ✅ Zero SpotBugs warnings
- ✅ Zero PMD violations
- ✅ Zero compiler warnings
- ✅ Full build passes: `mvn -q clean install`

---

## Verification Commands

```bash
# Static analysis
mvn -q compile checkstyle:check spotbugs:check pmd:check

# Run unit tests
mvn -q test -Dtest=UrlSecurityValidatorTest

# Run integration tests
mvn -q test -Dtest=SecurityControlsIT

# Full build
mvn -q clean install
```

---

## Security Impact

**Before Task 8:**
- ⚠️ SSRF vulnerability: Can scan internal network (10.0.0.0/8, 192.168.0.0/16)
- ⚠️ Data exfiltration: Can fetch data from localhost services
- ⚠️ File access: Can use file:// scheme to read local files

**After Task 8:**
- ✅ SSRF prevented: Private IPs and localhost blocked
- ✅ Scheme whitelist: Only http/https allowed
- ✅ Defense in depth: Multiple validation layers

---

## Next Steps

After completing this task:

1. **Verify** all success criteria met
2. **Commit** changes with conventional commit message
3. **Delete** this task file
4. **Update** `.tasks/shacl/README.md` (mark Task 8 as completed)
5. **Proceed** to Task 9: Performance Optimization (optional)

---

## References

- **[OWASP SSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)**
- **[RFC 1918](https://www.rfc-editor.org/rfc/rfc1918)** - Private IP address ranges
- **[Development Guidelines](../../.claude/CLAUDE.md)** - Security best practices

---

**Estimated Time:** 2-3 hours
**Complexity:** Medium
**Status:** Blocked by Task 7
