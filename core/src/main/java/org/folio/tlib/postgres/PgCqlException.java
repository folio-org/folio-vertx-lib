package org.folio.tlib.postgres;

import org.z3950.zing.cql.CQLTermNode;

/**
 * Thrown when processing CQL fails.
 */
public class PgCqlException extends RuntimeException {
  public PgCqlException(String msg) {
    super(msg);
  }

  /**
   * Creates an exception with the validation message.
   * This overload preserves binary compatibility for callers compiled against it.
   *
   * @param msg error description
   * @param cqlTermNode offending term, ignored to keep query values out of the message
   */
  public PgCqlException(String msg, CQLTermNode cqlTermNode) {
    this(msg);
  }
}
