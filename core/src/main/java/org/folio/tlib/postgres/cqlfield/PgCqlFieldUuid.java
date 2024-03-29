package org.folio.tlib.postgres.cqlfield;

import java.util.UUID;
import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * UUID {@link PgCqlFieldType}.
 */
public class PgCqlFieldUuid extends PgCqlFieldBase implements PgCqlFieldType {
  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    // convert to UUID so IllegalArgumentException is thrown if invalid
    // this also down-cases uppercase hex digits.
    try {
      UUID id = UUID.fromString(termNode.getTerm());

      String pgTerm = "'" + id + "'";
      String op = handleUnorderedRelation(termNode);
      return column + op + pgTerm;
    } catch (IllegalArgumentException e) {
      throw new PgCqlException("Invalid UUID", termNode);
    }
  }
}
