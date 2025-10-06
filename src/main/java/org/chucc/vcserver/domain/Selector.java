package org.chucc.vcserver.domain;

import org.chucc.vcserver.util.SelectorValidator;

/**
 * Value object representing version control selectors for SPARQL queries.
 * Per SPARQL 1.2 Protocol §4, selectors are mutually exclusive except for
 * the special case of asOf + branch (§3.2).
 */
public final class Selector {

  private final String branch;
  private final String commit;
  private final String asOf;

  private Selector(String branch, String commit, String asOf) {
    SelectorValidator.validateMutualExclusion(branch, commit, asOf);
    this.branch = normalize(branch);
    this.commit = normalize(commit);
    this.asOf = normalize(asOf);
  }

  /**
   * Creates a new Selector instance.
   *
   * @param branch the branch selector (may be null or blank)
   * @param commit the commit selector (may be null or blank)
   * @param asOf the asOf selector (may be null or blank)
   * @return a new Selector instance
   * @throws org.chucc.vcserver.exception.SelectorConflictException
   *     if selectors violate mutual exclusion rules
   */
  public static Selector of(String branch, String commit, String asOf) {
    return new Selector(branch, commit, asOf);
  }

  /**
   * Checks if branch selector is present.
   *
   * @return true if branch is not null and not blank
   */
  public boolean hasBranch() {
    return branch != null;
  }

  /**
   * Checks if commit selector is present.
   *
   * @return true if commit is not null and not blank
   */
  public boolean hasCommit() {
    return commit != null;
  }

  /**
   * Checks if asOf selector is present.
   *
   * @return true if asOf is not null and not blank
   */
  public boolean hasAsOf() {
    return asOf != null;
  }

  /**
   * Gets the branch selector.
   *
   * @return the branch selector, or null if not present
   */
  public String getBranch() {
    return branch;
  }

  /**
   * Gets the commit selector.
   *
   * @return the commit selector, or null if not present
   */
  public String getCommit() {
    return commit;
  }

  /**
   * Gets the asOf selector.
   *
   * @return the asOf selector, or null if not present
   */
  public String getAsOf() {
    return asOf;
  }

  private String normalize(String value) {
    return (value == null || value.isBlank()) ? null : value;
  }
}
