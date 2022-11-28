package org.folio.tlib.postgres.cqlfield;

import static org.folio.tlib.postgres.cqlfield.Util.basicOp;
import static org.folio.tlib.postgres.cqlfield.Util.handleNull;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldBoolean implements PgCqlFieldType {
  String column;

  @Override
  public String getColumn() {
    return column;
  }

  @Override
  public PgCqlFieldType withColumn(String column) {
    this.column = column;
    return this;
  }

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleNull(column, termNode);
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
      throw new IllegalArgumentException("Bad boolean for: " + termNode.toCQL());
    }
    return column + basicOp(termNode) + pgTerm;
  }
}
