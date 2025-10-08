package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for batch graph operations via POST /version/batch-graphs.
 * Contains multiple graph operations (PUT, POST, PATCH, DELETE) to be
 * executed atomically or sequentially.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchGraphsRequest {

  private String mode;
  private String branch;
  private String author;
  private String message;
  private List<GraphOperation> operations;

  /**
   * Default constructor for JSON deserialization.
   */
  public BatchGraphsRequest() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with all fields.
   *
   * @param mode the batch mode ("single" or "multiple")
   * @param branch the target branch name
   * @param author the commit author
   * @param message the commit message
   * @param operations the list of graph operations
   */
  public BatchGraphsRequest(
      String mode,
      String branch,
      String author,
      String message,
      List<GraphOperation> operations) {
    this.mode = mode;
    this.branch = branch;
    this.author = author;
    this.message = message;
    this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
  }

  /**
   * Validates that the request has valid fields.
   */
  public void validate() {
    if (branch == null || branch.isEmpty()) {
      throw new IllegalArgumentException("Branch must be provided");
    }
    if (mode == null || mode.isEmpty()) {
      throw new IllegalArgumentException("Mode must be provided");
    }
    if (!"single".equals(mode) && !"multiple".equals(mode)) {
      throw new IllegalArgumentException("Mode must be 'single' or 'multiple'");
    }
    if (operations == null || operations.isEmpty()) {
      throw new IllegalArgumentException("Operations list cannot be empty");
    }
    if (author == null || author.isEmpty()) {
      throw new IllegalArgumentException("Author must be provided");
    }
    if (message == null || message.isEmpty()) {
      throw new IllegalArgumentException("Message must be provided");
    }

    // Validate each operation
    for (int i = 0; i < operations.size(); i++) {
      try {
        operations.get(i).validate();
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Operation " + i + ": " + e.getMessage(), e);
      }
    }
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getAuthor() {
    return author;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<GraphOperation> getOperations() {
    return operations != null ? new ArrayList<>(operations) : new ArrayList<>();
  }

  public void setOperations(List<GraphOperation> operations) {
    this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
  }

  /**
   * Nested class representing a single graph operation.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class GraphOperation {

    private String method;
    private String graph;
    private String data;
    private String contentType;
    private String patch;

    /**
     * Default constructor for JSON deserialization.
     */
    public GraphOperation() {
      // Required for JSON deserialization
    }

    /**
     * Constructor with all fields.
     *
     * @param method the HTTP method (PUT, POST, PATCH, DELETE)
     * @param graph the graph IRI (null for default graph)
     * @param data the RDF content (for PUT/POST)
     * @param contentType the content type (for PUT/POST/PATCH)
     * @param patch the RDF Patch content (for PATCH)
     */
    public GraphOperation(
        String method,
        String graph,
        String data,
        String contentType,
        String patch) {
      this.method = method;
      this.graph = graph;
      this.data = data;
      this.contentType = contentType;
      this.patch = patch;
    }

    /**
     * Validates that the operation has valid fields.
     */
    public void validate() {
      if (method == null || method.isEmpty()) {
        throw new IllegalArgumentException("Method must be provided");
      }
      if (!"PUT".equals(method) && !"POST".equals(method)
          && !"PATCH".equals(method) && !"DELETE".equals(method)) {
        throw new IllegalArgumentException(
            "Method must be PUT, POST, PATCH, or DELETE");
      }

      // Validate fields based on method
      if ("PUT".equals(method) || "POST".equals(method)) {
        if (data == null || data.isEmpty()) {
          throw new IllegalArgumentException(
              "Data must be provided for " + method + " operation");
        }
        if (contentType == null || contentType.isEmpty()) {
          throw new IllegalArgumentException(
              "ContentType must be provided for " + method + " operation");
        }
      } else if ("PATCH".equals(method)) {
        if (patch == null || patch.isEmpty()) {
          throw new IllegalArgumentException(
              "Patch must be provided for PATCH operation");
        }
        if (graph == null || graph.isEmpty()) {
          throw new IllegalArgumentException(
              "Graph IRI must be provided for PATCH operation");
        }
      } else if ("DELETE".equals(method)) {
        if (graph == null || graph.isEmpty()) {
          throw new IllegalArgumentException(
              "Graph IRI must be provided for DELETE operation");
        }
      }
    }

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getGraph() {
      return graph;
    }

    public void setGraph(String graph) {
      this.graph = graph;
    }

    public String getData() {
      return data;
    }

    public void setData(String data) {
      this.data = data;
    }

    public String getContentType() {
      return contentType;
    }

    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    public String getPatch() {
      return patch;
    }

    public void setPatch(String patch) {
      this.patch = patch;
    }
  }
}
