package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldBoolean extends PgCqlFieldBase implements PgCqlFieldType {
  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    String cqlTerm = termNode.getTerm();
    String pgTerm;
    if ("false".equalsIgnoreCase(cqlTerm)) {
      pgTerm = "FALSE";
    } else if ("true".equalsIgnoreCase(cqlTerm)) {
      pgTerm = "TRUE";
    } else {
      throw new PgCqlException("Bad boolean", termNode);
    }
    return column + handleUnorderedRelation(termNode) + pgTerm;
  }
}
