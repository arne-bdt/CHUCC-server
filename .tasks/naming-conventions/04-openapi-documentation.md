# Task: Update OpenAPI Documentation with Identifier Format

**Status:** Not Started
**Priority:** High
**Estimated Time:** 2-3 hours

---

## Objective

Update OpenAPI specifications with detailed identifier format descriptions, examples, and validation rules for datasets, branches, tags, and commits.

---

## Background

Current OpenAPI specs likely have minimal validation annotations for identifier names. After implementing naming conventions in the protocol and code, we need to document these rules in the OpenAPI specs so API consumers understand the constraints.

---

## Files to Update

- `src/main/resources/openapi/openapi.yaml` (if exists)
- Or annotations in controller classes (if using Springdoc)

---

## Implementation Plan

### 1. Define Reusable Schema Components

Add to `components.schemas` section in OpenAPI YAML:

```yaml
components:
  schemas:
    # Identifier patterns
    DatasetName:
      type: string
      pattern: '^[A-Za-z0-9._-]+$'
      minLength: 1
      maxLength: 249
      example: 'biodiversity-2025'
      description: |
        Dataset name must:
        - Match pattern: `[A-Za-z0-9._-]`
        - Be 1-249 characters long (Kafka topic limit)
        - Be in Unicode NFC normalization form
        - Not be `.` or `..`
        - Not start with `_` (reserved for internal topics)

        Examples: `mydata`, `species-db`, `project.v2`

    BranchName:
      type: string
      pattern: '^[A-Za-z0-9._-]+$'
      minLength: 1
      maxLength: 255
      example: 'feature-login'
      description: |
        Branch name must:
        - Match pattern: `[A-Za-z0-9._-]`
        - Be 1-255 characters long
        - Be in Unicode NFC normalization form
        - Not be `.` or `..`
        - Not start or end with `.`
        - Not start with `_` (reserved for internal use)

        Examples: `main`, `develop`, `feature-auth`, `release.v2`

        Note: Use `.` or `-` for hierarchy (not `/`):
        - `feature.login` (dot notation)
        - `feature-login` (hyphen notation)

    TagName:
      type: string
      pattern: '^[A-Za-z0-9._-]+$'
      minLength: 1
      maxLength: 255
      example: 'v1.0.0'
      description: |
        Tag name must:
        - Match pattern: `[A-Za-z0-9._-]`
        - Be 1-255 characters long
        - Be in Unicode NFC normalization form
        - Not be `.` or `..`
        - Not start or end with `.`
        - Not start with `_` (reserved for internal use)
        - Tags are immutable once created

        Examples: `v1.0.0`, `stable`, `release-2025-01`, `beta.1`

    CommitId:
      type: string
      pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$'
      example: '01936d8f-1234-7890-abcd-ef1234567890'
      description: |
        UUIDv7 commit identifier:
        - Format: 8hex-4hex-4hex-4hex-12hex
        - Version nibble must be 7 (4th group starts with 7)
        - Lowercase hexadecimal
        - Immutable and globally unique
        - Timestamp-based (sortable)
```

---

### 2. Update Request/Response Models

#### CreateDatasetRequest

```yaml
CreateDatasetRequest:
  type: object
  required:
    - description
  properties:
    description:
      type: string
      example: 'Biodiversity research dataset'
      description: 'Human-readable description of the dataset'
    initialGraph:
      type: string
      format: uri
      example: 'http://example.org/default'
      description: 'Optional IRI for the initial default graph'
    kafka:
      $ref: '#/components/schemas/KafkaTopicConfig'

CreateDatasetResponse:
  type: object
  properties:
    dataset:
      $ref: '#/components/schemas/DatasetName'
    mainBranch:
      $ref: '#/components/schemas/BranchName'
    initialCommitId:
      $ref: '#/components/schemas/CommitId'
    kafkaTopic:
      type: string
      example: 'vc.biodiversity-2025.events'
    message:
      type: string
      example: 'Dataset created successfully'
```

#### CreateBranchRequest

```yaml
CreateBranchRequest:
  type: object
  required:
    - name
    - from
  properties:
    name:
      $ref: '#/components/schemas/BranchName'
    from:
      type: string
      description: 'Source reference (branch name, tag name, or commit ID)'
      example: 'main'
    isProtected:
      type: boolean
      default: false
      description: 'Whether this branch is protected from deletion/force-push'

BranchResponse:
  type: object
  properties:
    name:
      $ref: '#/components/schemas/BranchName'
    commitId:
      $ref: '#/components/schemas/CommitId'
    isProtected:
      type: boolean
    createdAt:
      type: string
      format: date-time
    lastUpdated:
      type: string
      format: date-time
    commitCount:
      type: integer
      minimum: 1
```

#### CreateTagRequest

```yaml
CreateTagRequest:
  type: object
  required:
    - name
    - target
  properties:
    name:
      $ref: '#/components/schemas/TagName'
    target:
      $ref: '#/components/schemas/CommitId'
    message:
      type: string
      example: 'First stable release'
      description: 'Optional annotation message'
    author:
      type: string
      example: 'Alice <alice@example.org>'
      description: 'Optional tag author'

TagResponse:
  type: object
  properties:
    name:
      $ref: '#/components/schemas/TagName'
    commitId:
      $ref: '#/components/schemas/CommitId'
    message:
      type: string
      nullable: true
    author:
      type: string
      nullable: true
    createdAt:
      type: string
      format: date-time
```

---

### 3. Update Path Parameters

```yaml
paths:
  # Dataset operations
  /datasets/{dataset}:
    parameters:
      - name: dataset
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/DatasetName'
        description: 'Dataset identifier'
    get:
      summary: 'Get dataset metadata'
      # ...
    delete:
      summary: 'Delete dataset'
      # ...

  # Branch operations
  /{dataset}/version/branches/{branch}:
    parameters:
      - name: dataset
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/DatasetName'
      - name: branch
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/BranchName'
        description: 'Branch name'
    get:
      summary: 'Get branch details'
      # ...

  # Tag operations
  /{dataset}/version/tags/{tag}:
    parameters:
      - name: dataset
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/DatasetName'
      - name: tag
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/TagName'
        description: 'Tag name'
    get:
      summary: 'Get tag details'
      # ...

  # Commit operations
  /{dataset}/version/commits/{commitId}:
    parameters:
      - name: dataset
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/DatasetName'
      - name: commitId
        in: path
        required: true
        schema:
          $ref: '#/components/schemas/CommitId'
        description: 'Commit identifier (UUIDv7)'
    get:
      summary: 'Get commit details'
      # ...
```

---

### 4. Add Error Response Examples

```yaml
components:
  responses:
    InvalidIdentifierName:
      description: 'Invalid identifier name'
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
          examples:
            invalidCharacters:
              summary: 'Invalid characters in name'
              value:
                type: 'about:blank'
                title: 'Bad Request'
                status: 400
                code: 'invalid_identifier'
                detail: 'Branch name contains invalid characters. Allowed: A-Z, a-z, 0-9, . (period), _ (underscore), - (hyphen)'

            nameTooLong:
              summary: 'Name exceeds maximum length'
              value:
                type: 'about:blank'
                title: 'Bad Request'
                status: 400
                code: 'invalid_identifier'
                detail: 'Branch name too long (max 255 characters)'

            reservedName:
              summary: 'Reserved name'
              value:
                type: 'about:blank'
                title: 'Bad Request'
                status: 400
                code: 'invalid_identifier'
                detail: 'Dataset name cannot be a Windows reserved device name: CON'

            notNormalized:
              summary: 'Not in Unicode NFC form'
              value:
                type: 'about:blank'
                title: 'Bad Request'
                status: 400
                code: 'invalid_identifier'
                detail: 'Tag name must be in Unicode NFC normalization form'
```

---

### 5. Add Naming Conventions Guide

Add to OpenAPI `info.description`:

```yaml
info:
  title: 'CHUCC SPARQL Version Control API'
  version: '1.0.0'
  description: |
    # SPARQL 1.2 Protocol with Version Control

    This API implements version control for SPARQL datasets, supporting:
    - Commit-based version history
    - Branch and tag management
    - Time-travel queries
    - Merge operations

    ## Naming Conventions

    All identifiers (datasets, branches, tags) must follow these rules:

    ### Pattern
    - **Allowed characters:** `A-Z`, `a-z`, `0-9`, `.` (period), `_` (underscore), `-` (hyphen)
    - **Regular expression:** `^[A-Za-z0-9._-]+$`
    - **Case-sensitive:** `Main` ≠ `main`

    ### Length Limits
    - **Datasets:** 1-249 characters (Kafka topic limit)
    - **Branches:** 1-255 characters
    - **Tags:** 1-255 characters
    - **Commits:** Exactly 36 characters (UUIDv7 format)

    ### Reserved Names
    - Cannot be `.` or `..`
    - SHOULD NOT start with `_` (reserved for internal use)
    - Cannot be Windows device names (`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`)

    ### Unicode
    - Must be in **Unicode NFC normalization form**
    - Prevents homograph attacks (e.g., Latin 'a' vs. Cyrillic 'а')

    ### Hierarchical Names
    Use `.` or `-` for hierarchy (NOT `/`):
    - **Dot notation:** `feature.authentication.oauth` (like Java packages)
    - **Hyphen notation:** `feature-authentication-oauth`
    - **Underscore notation:** `feature_authentication_oauth`

    ### Examples

    **Valid:**
    ```
    mydata
    biodiversity-2025
    species.v2.1
    release_1.0
    ```

    **Invalid:**
    ```
    my/data           # Contains / (URL ambiguity)
    my data           # Contains space
    .hidden           # Starts with .
    __internal        # Starts with __
    CON               # Windows reserved name
    ```

    ## URL Encoding

    Identifiers consist only of RFC 3986 "unreserved characters" - **no percent-encoding needed**:

    ```http
    GET /mydata/version/branches/feature-login/sparql
    GET /mydata/version/tags/v1.0.0/data?default
    ```

    **Do NOT percent-encode identifiers** - this creates invalid resource references.

    For more details, see: `protocol/NAMING_CONVENTIONS.md`
```

---

## Implementation Steps (Using Springdoc Annotations)

If using Springdoc (Spring Boot + OpenAPI annotations):

### 1. Add Schema Annotations to DTOs

**CreateDatasetRequest.java:**

```java
@Schema(description = "Request to create a new dataset")
public record CreateDatasetRequest(
    @Schema(
        description = "Human-readable description of the dataset",
        example = "Biodiversity research dataset"
    )
    String description,

    @Schema(
        description = "Optional IRI for the initial default graph",
        example = "http://example.org/default"
    )
    String initialGraph,

    @Schema(description = "Optional Kafka topic configuration")
    KafkaTopicConfig kafka
) {
  // ...
}
```

### 2. Add Parameter Annotations to Controllers

**DatasetController.java:**

```java
@RestController
@RequestMapping("/datasets")
@Tag(name = "Datasets", description = "Dataset management operations")
public class DatasetController {

  @PostMapping("/{dataset}")
  @Operation(
      summary = "Create a new dataset",
      description = """
          Creates a new dataset with automatic Kafka topic creation.

          Dataset name must:
          - Match pattern: [A-Za-z0-9._-]
          - Be 1-249 characters (Kafka limit)
          - Not be . or ..
          - Not start with _ (reserved)
          """
  )
  @ApiResponses(value = {
      @ApiResponse(responseCode = "202", description = "Dataset created successfully"),
      @ApiResponse(
          responseCode = "400",
          description = "Invalid dataset name",
          content = @Content(
              mediaType = "application/problem+json",
              examples = {
                  @ExampleObject(
                      name = "invalidCharacters",
                      value = """
                          {
                            "type": "about:blank",
                            "title": "Bad Request",
                            "status": 400,
                            "code": "invalid_identifier",
                            "detail": "Dataset name contains invalid characters"
                          }
                          """
                  )
              }
          )
      )
  })
  public ResponseEntity<DatasetCreationResponse> createDataset(
      @Parameter(
          description = "Dataset name (pattern: [A-Za-z0-9._-], max 249 chars)",
          example = "biodiversity-2025",
          schema = @Schema(
              pattern = "^[A-Za-z0-9._-]+$",
              minLength = 1,
              maxLength = 249
          )
      )
      @PathVariable String dataset,

      @RequestBody CreateDatasetRequest request,
      @RequestHeader("SPARQL-VC-Author") String author
  ) {
    // ...
  }
}
```

**BranchController.java:**

```java
@GetMapping("/{dataset}/version/branches/{branch}")
@Operation(
    summary = "Get branch details",
    description = "Retrieve metadata for a specific branch"
)
public ResponseEntity<BranchResponse> getBranch(
    @Parameter(
        description = "Dataset name",
        example = "mydata",
        schema = @Schema(
            pattern = "^[A-Za-z0-9._-]+$",
            maxLength = 249
        )
    )
    @PathVariable String dataset,

    @Parameter(
        description = """
            Branch name (pattern: [A-Za-z0-9._-], max 255 chars)
            Use . or - for hierarchy: feature.login or feature-login
            """,
        example = "feature-login",
        schema = @Schema(
            pattern = "^[A-Za-z0-9._-]+$",
            maxLength = 255
        )
    )
    @PathVariable String branch
) {
  // ...
}
```

---

## Acceptance Criteria

- ✅ OpenAPI spec includes identifier pattern definitions
- ✅ All endpoints have parameter descriptions with examples
- ✅ Error responses documented with examples
- ✅ Naming conventions guide in API description
- ✅ Valid/invalid examples provided
- ✅ Swagger UI shows validation rules clearly
- ✅ Generated client libraries include validation

---

## Testing

1. **Generate OpenAPI spec:**
   ```bash
   mvn clean package
   # Access: http://localhost:8080/v3/api-docs
   ```

2. **Validate OpenAPI spec:**
   ```bash
   npx @redocly/cli lint openapi.yaml
   ```

3. **Test Swagger UI:**
   - Navigate to: `http://localhost:8080/swagger-ui.html`
   - Verify identifier patterns show in parameter descriptions
   - Test validation in "Try it out" with invalid names
   - Check error response examples

4. **Generate client:**
   ```bash
   npx @openapitools/openapi-generator-cli generate \
     -i openapi.yaml \
     -g typescript-fetch \
     -o ./generated-client

   # Verify client includes validation
   cat generated-client/models/DatasetName.ts
   ```

---

## References

- OpenAPI 3.1 spec: https://spec.openapis.org/oas/v3.1.0
- Springdoc annotations: https://springdoc.org/
- Pattern validation: https://json-schema.org/understanding-json-schema/reference/string.html#pattern
- Protocol naming conventions: `protocol/NAMING_CONVENTIONS.md`

---

## Follow-up Tasks

- Generate client libraries with updated specs (TypeScript, Python, Java)
- Update API documentation website
- Add examples to postman collection
- Consider adding JSON Schema validation for request bodies
