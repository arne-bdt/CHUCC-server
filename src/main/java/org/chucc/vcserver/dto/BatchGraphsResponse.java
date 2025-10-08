package org.chucc.vcserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for batch graph operations.
 * Contains the list of commits created by the batch operation.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchGraphsResponse {

  private List<BatchCommit> commits;

  /**
   * Default constructor for JSON deserialization.
   */
  public BatchGraphsResponse() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with commits list.
   *
   * @param commits the list of commits created
   */
  public BatchGraphsResponse(List<BatchCommit> commits) {
    this.commits = commits != null ? new ArrayList<>(commits) : new ArrayList<>();
  }

  public List<BatchCommit> getCommits() {
    return commits != null ? new ArrayList<>(commits) : new ArrayList<>();
  }

  public void setCommits(List<BatchCommit> commits) {
    this.commits = commits != null ? new ArrayList<>(commits) : new ArrayList<>();
  }

  /**
   * Nested class representing a commit in the batch response.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class BatchCommit {

    private String id;
    private List<String> operations;

    /**
     * Default constructor for JSON deserialization.
     */
    public BatchCommit() {
      // Required for JSON deserialization
    }

    /**
     * Constructor with all fields.
     *
     * @param id the commit ID (UUIDv7)
     * @param operations the list of operation descriptions
     */
    public BatchCommit(String id, List<String> operations) {
      this.id = id;
      this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public List<String> getOperations() {
      return operations != null ? new ArrayList<>(operations) : new ArrayList<>();
    }

    public void setOperations(List<String> operations) {
      this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
    }
  }
}
