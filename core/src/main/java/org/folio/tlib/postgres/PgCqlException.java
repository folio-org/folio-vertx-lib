package org.folio.tlib.postgres;

import org.z3950.zing.cql.CQLTermNode;

/**
 * Thrown when processing CQL fails.
 */
public class PgCqlException extends RuntimeException {
  public PgCqlException(String msg) {
    super(msg);
  }

  public PgCqlException(String msg, CQLTermNode cqlTermNode) {
    super(msg + " for: " + cqlTermNode.toCQL());
  }
}
