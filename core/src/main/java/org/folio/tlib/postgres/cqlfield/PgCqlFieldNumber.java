package org.folio.tlib.postgres.cqlfield;

import static org.folio.tlib.postgres.cqlfield.Util.handleEmptyTerm;
import static org.folio.tlib.postgres.cqlfield.Util.numberOp;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldNumber implements PgCqlFieldType {
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
    String s = handleEmptyTerm(column, termNode);
    if (s != null) {
      return s;
    }
    String cqlTerm = termNode.getTerm();
    if (cqlTerm.isEmpty()) {
      throw new IllegalArgumentException("Bad numeric for: " + termNode.toCQL());
    }
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      switch (c) {
        case '.':
        case 'e':
        case 'E':
        case '+':
        case '-':
          break;
        default:
          if (!Character.isDigit(c)) {
            throw new IllegalArgumentException("Bad numeric for: " + termNode.toCQL());
          }
      }
    }
    return column + numberOp(termNode) + cqlTerm;
  }
}
