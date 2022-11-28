package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldAlwaysMatches implements PgCqlFieldType {
  @Override
  public String getColumn() {
    return null;
  }

  @Override
  public PgCqlFieldType withColumn(String column) {
    return this;
  }

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    return null;
  }
}
