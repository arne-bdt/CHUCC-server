package org.chucc.vcserver.dto;

/**
 * Represents a single conflict item in a merge/rebase/cherry-pick operation.
 * All fields are REQUIRED per SPARQL 1.2 Protocol Version Control Extension §11.
 */
public class ConflictItem {

  private String graph;
  private String subject;
  private String predicate;
  private String object;
  private String details;

  /**
   * Default constructor for JSON deserialization.
   */
  public ConflictItem() {
    // Required for JSON deserialization
  }

  /**
   * Constructor with required fields.
   *
   * @param graph the graph URI
   * @param subject the subject URI
   * @param predicate the predicate URI
   * @param object the object value
   */
  public ConflictItem(String graph, String subject, String predicate, String object) {
    this.graph = graph;
    this.subject = subject;
    this.predicate = predicate;
    this.object = object;
  }

  /**
   * Constructor with all fields.
   *
   * @param graph the graph URI
   * @param subject the subject URI
   * @param predicate the predicate URI
   * @param object the object value
   * @param details optional details about the conflict
   */
  public ConflictItem(String graph, String subject, String predicate, String object,
                      String details) {
    this.graph = graph;
    this.subject = subject;
    this.predicate = predicate;
    this.object = object;
    this.details = details;
  }

  public String getGraph() {
    return graph;
  }

  public void setGraph(String graph) {
    this.graph = graph;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getPredicate() {
    return predicate;
  }

  public void setPredicate(String predicate) {
    this.predicate = predicate;
  }

  public String getObject() {
    return object;
  }

  public void setObject(String object) {
    this.object = object;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }
}
