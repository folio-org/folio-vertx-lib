package org.folio.tlib.postgres.cqlfield;

import java.util.regex.Pattern;
import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldNumber extends PgCqlFieldBase implements PgCqlFieldType {

  private static final Pattern POSTGRES_NUMBER_REGEXP = Pattern.compile(
      "[+-]?"
          + "(?:"
          + "\\d+"
          + "|\\d+\\.\\d*"
          + "|\\.\\d+"
          + ")"
          + "(?:[eE][+-]?\\d+)?"
  );

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    String cqlTerm = termNode.getTerm();
    if (!POSTGRES_NUMBER_REGEXP.matcher(cqlTerm).matches()) {
      throw new PgCqlException("Bad numeric", termNode);
    }
    return column + handleOrderedRelation(termNode) + cqlTerm;
  }
}
