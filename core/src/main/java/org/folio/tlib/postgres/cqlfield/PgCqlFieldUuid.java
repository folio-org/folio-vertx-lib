package org.folio.tlib.postgres.cqlfield;

import java.util.UUID;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

import static org.folio.tlib.postgres.cqlfield.Util.basicOp;
import static org.folio.tlib.postgres.cqlfield.Util.handleNull;

public class PgCqlFieldUuid implements PgCqlFieldType {

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
    // convert to UUID so IllegalArgumentException is thrown if invalid
    // this also down-cases uppercase hex digits.
    try {
      UUID id = UUID.fromString(termNode.getTerm());

      String pgTerm = "'" + id + "'";
      String op = basicOp(termNode);
      return column + op + pgTerm;
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Invalid UUID in " + termNode.toCQL());
    }
  }
}
