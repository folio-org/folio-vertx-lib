package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldAlwaysMatches extends PgCqlFieldBase implements PgCqlFieldType {
  @Override
  public String handleTermNode(CQLTermNode termNode) {
    return null;
  }
}
