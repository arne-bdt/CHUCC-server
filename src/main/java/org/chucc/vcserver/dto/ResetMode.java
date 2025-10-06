package org.chucc.vcserver.dto;

/**
 * Reset mode for branch reset operations.
 * Defines the behavior when moving a branch pointer to a different commit.
 *
 * <ul>
 *   <li><b>HARD</b>: Move branch pointer and reset materialized dataset to target commit state</li>
 *   <li><b>SOFT</b>: Move branch pointer only, preserve uncommitted changes</li>
 *   <li><b>MIXED</b>: Move branch pointer, partially preserve changes (semantics TBD for RDF)</li>
 * </ul>
 */
public enum ResetMode {
  /**
   * Hard reset: move branch pointer and reset materialized dataset to target commit.
   */
  HARD,

  /**
   * Soft reset: move branch pointer only, keep uncommitted changes.
   */
  SOFT,

  /**
   * Mixed reset: move branch pointer, partial preservation of changes.
   * Note: Mixed mode semantics for RDF datasets are implementation-specific.
   */
  MIXED
}
