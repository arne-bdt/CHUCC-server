package org.chucc.vcserver.util;

import org.apache.jena.graph.Node;
import org.apache.jena.rdfpatch.RDFChanges;

/**
 * Abstract adapter class for RDFChanges providing no-op implementations
 * of all methods except add() and delete().
 *
 * <p>This class eliminates boilerplate when implementing RDFChanges
 * for simple use cases that only care about quad additions/deletions.
 */
public abstract class RdfChangesAdapter implements RDFChanges {

  @Override
  public void start() {}

  @Override
  public void finish() {}

  @Override
  public void header(String field, Node value) {}

  @Override
  public void addPrefix(Node gn, String prefix, String uriStr) {}

  @Override
  public void deletePrefix(Node gn, String prefix) {}

  @Override
  public void txnBegin() {}

  @Override
  public void txnCommit() {}

  @Override
  public void txnAbort() {}

  @Override
  public void segment() {}
}
