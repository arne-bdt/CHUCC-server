package org.chucc.vcserver.util;

import org.chucc.vcserver.exception.SelectorConflictException;

/**
 * Utility class for validating selector mutual exclusion.
 * Per SPARQL 1.2 Protocol §4, selectors (branch, commit, asOf) are mutually exclusive,
 * with the exception that asOf + branch is allowed per §3.2.
 */
public final class SelectorValidator {

  private SelectorValidator() {
    // Utility class - prevent instantiation
  }

  /**
   * Validates that selectors are mutually exclusive.
   *
   * @param branch the branch selector (may be null or blank)
   * @param commit the commit selector (may be null or blank)
   * @param asOf the asOf selector (may be null or blank)
   * @throws SelectorConflictException if selectors violate mutual exclusion rules
   */
  public static void validateMutualExclusion(String branch, String commit, String asOf) {
    int count = count(branch) + count(commit) + count(asOf);

    if (count == 0) {
      return; // All null is valid (use default)
    }

    if (count == 1) {
      return; // Exactly one is valid
    }

    if (count == 2 && branch != null && !branch.isBlank()
        && asOf != null && !asOf.isBlank()
        && (commit == null || commit.isBlank())) {
      return; // asOf + branch is allowed per §3.2
    }

    throw new SelectorConflictException(
        "Selectors branch, commit, and asOf are mutually exclusive (except asOf+branch)"
    );
  }

  private static int count(String s) {
    return s != null && !s.isBlank() ? 1 : 0;
  }
}
