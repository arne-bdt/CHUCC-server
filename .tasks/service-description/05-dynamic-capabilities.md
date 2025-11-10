# Phase 5: Dynamic Capabilities Detection

**Status:** ✅ COMPLETED (2025-11-10)
**Priority:** Low
**Estimated Time:** 1-2 hours
**Complexity:** Low

---

## Overview

Add dynamic detection of SPARQL features and content types supported by the implementation. Rather than hardcoding capabilities, detect them from the actual Jena implementation and Spring configuration.

**Goal:** Ensure service description accurately reflects actual capabilities.

---

## Prerequisites

- ✅ Phase 4 completed (version control extension working)
- ✅ Service description exposes datasets, graphs, branches, tags

---

## Implementation Steps

### Step 1: Detect SPARQL Features from Jena

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

```java
private void addFeatures(Model model, Resource service) {
  // Standard SPARQL 1.1 features (always supported by Jena)
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(SD_NS + "UnionDefaultGraph"));

  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(SD_NS + "BasicFederatedQuery"));

  // Optional features (check Jena capabilities)
  // Property Paths - supported by Jena ARQ
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(SD_NS + "PropertyPaths"));

  // Aggregates - supported by Jena ARQ
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(SD_NS + "Aggregates"));

  // Subqueries - supported by Jena ARQ
  service.addProperty(
      model.createProperty(SD_NS + "feature"),
      model.createResource(SD_NS + "SubQueries"));
}
```

---

### Step 2: Dynamically Detect Result Formats

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

```java
private void addResultFormats(Model model, Resource service) {
  // SPARQL Results formats (from RDFLanguages)
  addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_JSON");
  addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_XML");
  addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_CSV");
  addResultFormat(service, model, "http://www.w3.org/ns/formats/SPARQL_Results_TSV");

  // RDF serialization formats (supported by Jena)
  for (Lang lang : RDFLanguages.getRegisteredLanguages()) {
    if (lang.equals(Lang.TURTLE) ||
        lang.equals(Lang.RDFXML) ||
        lang.equals(Lang.JSONLD) ||
        lang.equals(Lang.NTRIPLES) ||
        lang.equals(Lang.NQUADS) ||
        lang.equals(Lang.TRIG)) {
      String formatUri = "http://www.w3.org/ns/formats/" + lang.getName();
      addResultFormat(service, model, formatUri);
    }
  }
}

private void addResultFormat(Resource service, Model model, String formatUri) {
  service.addProperty(
      model.createProperty(SD_NS + "resultFormat"),
      model.createResource(formatUri));
}
```

---

### Step 3: Add Input Format Detection

**Location:** `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

```java
private void addInputFormats(Model model, Resource service) {
  // Input formats for SPARQL UPDATE and GSP
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/Turtle"));
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/RDF_XML"));
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/JSON-LD"));
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/N-Triples"));
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/N-Quads"));
  service.addProperty(
      model.createProperty(SD_NS + "inputFormat"),
      model.createResource("http://www.w3.org/ns/formats/TriG"));
}
```

---

### Step 4: Add Tests for Dynamic Capabilities

**Location:** `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

```java
@Test
void serviceDescription_shouldDescribeSparqlFeatures() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert - SPARQL 1.1 features
  assertThat(response.getBody()).contains("sd:PropertyPaths");
  assertThat(response.getBody()).contains("sd:Aggregates");
  assertThat(response.getBody()).contains("sd:SubQueries");
}

@Test
void serviceDescription_shouldDescribeAllResultFormats() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert - Result formats
  assertThat(response.getBody()).contains("SPARQL_Results_JSON");
  assertThat(response.getBody()).contains("SPARQL_Results_XML");
  assertThat(response.getBody()).contains("SPARQL_Results_CSV");
  assertThat(response.getBody()).contains("Turtle");
  assertThat(response.getBody()).contains("JSON-LD");
}

@Test
void serviceDescription_shouldDescribeInputFormats() {
  // Act
  ResponseEntity<String> response = restTemplate.getForEntity(
      "/service-description",
      String.class);

  // Assert - Input formats
  assertThat(response.getBody()).contains("sd:inputFormat");
}
```

---

## Files to Modify

1. **Service**
   - `src/main/java/org/chucc/vcserver/service/ServiceDescriptionService.java`

2. **Tests**
   - `src/test/java/org/chucc/vcserver/integration/ServiceDescriptionIT.java`

---

## Testing Strategy

### Integration Tests

```bash
mvn -q test -Dtest=ServiceDescriptionIT
```

**New tests:**
- ✅ Service description lists SPARQL features
- ✅ Service description lists all result formats
- ✅ Service description lists input formats

---

## Success Criteria

- ✅ SPARQL features accurately described
- ✅ All result formats listed
- ✅ Input formats listed
- ✅ Capabilities reflect actual Jena implementation
- ✅ All tests pass
- ✅ Zero quality violations

---

## Build Commands

```bash
# Full build
mvn -q clean install
```

---

## Next Phase

**Phase 6:** [Documentation](./06-documentation.md)
- Update API documentation
- Create vocabulary reference
- Update architecture diagrams

---

**Estimated Time:** 1-2 hours
**Complexity:** Low
