package org.chucc.vcserver.controller;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.chucc.vcserver.command.CreateTagCommand;
import org.chucc.vcserver.command.CreateTagCommandHandler;
import org.chucc.vcserver.controller.util.PaginationValidator;
import org.chucc.vcserver.controller.util.ResponseHeaderBuilder;
import org.chucc.vcserver.controller.util.VersionControlUrls;
import org.chucc.vcserver.dto.CreateTagRequest;
import org.chucc.vcserver.dto.ProblemDetail;
import org.chucc.vcserver.dto.TagInfo;
import org.chucc.vcserver.dto.TagListResponse;
import org.chucc.vcserver.event.TagCreatedEvent;
import org.chucc.vcserver.service.TagService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Tag management endpoints for version control.
 */
@RestController
@RequestMapping("/{dataset}/version/tags")
@Tag(name = "Version Control", description = "Tag management operations")
public class TagController {

  private final TagService tagService;
  private final CreateTagCommandHandler createTagCommandHandler;

  /**
   * Constructor for TagController.
   *
   * @param tagService the tag service
   * @param createTagCommandHandler the create tag command handler
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Spring-managed beans are intentionally shared references"
  )
  public TagController(
      TagService tagService,
      CreateTagCommandHandler createTagCommandHandler) {
    this.tagService = tagService;
    this.createTagCommandHandler = createTagCommandHandler;
  }

  /**
   * List all tags with pagination.
   *
   * @param dataset the dataset name
   * @param limit maximum number of results to return
   * @param offset number of results to skip
   * @return paginated list of tags
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "List tags",
      description = "Returns a paginated list of all tags in the dataset"
  )
  @ApiResponse(
      responseCode = "200",
      description = "Tag list returned successfully",
      headers = {
          @Header(
              name = "Content-Location",
              description = "Canonical URL for this resource",
              schema = @Schema(type = "string")
          ),
          @Header(
              name = "Link",
              description = "RFC 5988 pagination links (next page when available)",
              schema = @Schema(type = "string")
          )
      },
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Bad Request - Invalid pagination parameters",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<TagListResponse> listTags(
      @Parameter(description = "Dataset name", example = "default", required = true)
      @PathVariable String dataset,
      @Parameter(description = "Maximum number of results (max 1000)", example = "100")
      @RequestParam(required = false, defaultValue = "100") Integer limit,
      @Parameter(description = "Offset for pagination", example = "0")
      @RequestParam(required = false, defaultValue = "0") Integer offset
  ) {
    // Validate pagination parameters
    PaginationValidator.validate(limit, offset);

    // Call service with pagination
    TagListResponse response = tagService.listTags(dataset, limit, offset);

    // Build headers
    HttpHeaders headers = new HttpHeaders();
    ResponseHeaderBuilder.addContentLocation(headers, VersionControlUrls.tags(dataset));

    // Add Link header for next page (RFC 5988)
    if (response.pagination().hasMore()) {
      String nextUrl = buildNextPageUrl(dataset, limit, offset);
      headers.add("Link", String.format("<%s>; rel=\"next\"", nextUrl));
    }

    return ResponseEntity.ok().headers(headers).body(response);
  }

  /**
   * Builds the URL for the next page in pagination.
   *
   * @param dataset dataset name
   * @param limit page limit
   * @param offset current offset
   * @return URL for next page
   */
  private String buildNextPageUrl(String dataset, int limit, int offset) {
    return String.format("/%s/version/tags?offset=%d&limit=%d",
        dataset, offset + limit, limit);
  }

  /**
   * Create a new immutable tag.
   *
   * @param dataset the dataset name
   * @param request tag creation request
   * @param authorHeader optional X-Author header
   * @return created tag
   */
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
  )
  @Operation(
      summary = "Create tag (immutable)",
      description = "Create a new immutable tag pointing to a commit"
  )
  @ApiResponse(
      responseCode = "202",
      description = "Tag accepted for creation",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
  )
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request (bad tag name or missing fields)",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "404",
      description = "Target commit not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "409",
      description = "Tag already exists",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<?> createTag(
      @PathVariable String dataset,
      @RequestBody CreateTagRequest request,
      @RequestHeader(value = "X-Author", required = false) String authorHeader
  ) {
    // Validate request
    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(),
              HttpStatus.BAD_REQUEST.value(), "INVALID_REQUEST"));
    }

    // Determine author (request body > header > anonymous)
    String author = request.author() != null ? request.author()
        : (authorHeader != null ? authorHeader : "anonymous");

    // Create command
    CreateTagCommand command = new CreateTagCommand(
        dataset,
        request.name(),
        request.target(),
        request.message(),
        author
    );

    // Handle command
    try {
      TagCreatedEvent event = (TagCreatedEvent) createTagCommandHandler.handle(command);

      // Build response (reuse TagInfo DTO)
      TagInfo response = new TagInfo(
          event.tagName(),
          event.commitId(),
          event.message(),
          event.author(),
          event.timestamp()
      );

      // Build Location URI
      String location = ServletUriComponentsBuilder
          .fromCurrentRequest()
          .path("/{name}")
          .buildAndExpand(event.tagName())
          .toUriString();

      return ResponseEntity
          .accepted()
          .header("Location", location)
          .header("SPARQL-VC-Status", "pending")
          .contentType(MediaType.APPLICATION_JSON)
          .body(response);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(),
              HttpStatus.CONFLICT.value(), "TAG_EXISTS"));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .body(new ProblemDetail(e.getMessage(),
              HttpStatus.NOT_FOUND.value(), "COMMIT_NOT_FOUND"));
    }
  }

  /**
   * Get tag details.
   *
   * @param dataset the dataset name
   * @param name the tag name
   * @return tag details if found, 404 otherwise
   */
  @GetMapping(value = "/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Get tag details",
      description = "Retrieve tag details and target commit"
  )
  @ApiResponse(responseCode = "200", description = "Tag details")
  @ApiResponse(
      responseCode = "404",
      description = "Tag not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @SuppressWarnings("PMD.LooseCoupling") // HttpHeaders provides Spring-specific utility methods
  public ResponseEntity<?> getTag(
      @PathVariable String dataset,
      @PathVariable String name
  ) {
    return tagService.getTagDetails(dataset, name)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(() -> {
          ProblemDetail problem = new ProblemDetail(
              "Tag not found: " + name,
              HttpStatus.NOT_FOUND.value(),
              "tag_not_found"
          );
          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.parseMediaType("application/problem+json"));
          return new ResponseEntity<>(problem, headers, HttpStatus.NOT_FOUND);
        });
  }

  /**
   * Delete a tag.
   *
   * @param dataset the dataset name
   * @param name the tag name
   * @return 204 if deleted, 404 if not found, 403 if forbidden
   */
  @DeleteMapping("/{name}")
  @Operation(
      summary = "Delete tag",
      description = "Delete a tag (if server policy allows)"
  )
  @ApiResponse(responseCode = "204", description = "Tag deleted")
  @ApiResponse(
      responseCode = "404",
      description = "Tag not found",
      content = @Content(mediaType = "application/problem+json")
  )
  @ApiResponse(
      responseCode = "403",
      description = "Forbidden - server policy prohibits tag deletion",
      content = @Content(mediaType = "application/problem+json")
  )
  public ResponseEntity<Void> deleteTag(
      @PathVariable String dataset,
      @PathVariable String name
  ) {
    tagService.deleteTag(dataset, name);

    // Build response headers
    HttpHeaders headers = new HttpHeaders();
    headers.set("SPARQL-VC-Status", "pending");

    return ResponseEntity.accepted().headers(headers).build();
  }
}
