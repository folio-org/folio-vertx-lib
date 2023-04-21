package org.folio.tlib.postgres.cqlfield;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Create field for ISO-like date-only and date-time expressions (e.g TIMESTAMP or DATE) w/o offset.
 */
public class PgCqlFieldTimestamp extends PgCqlFieldBase implements PgCqlFieldType {
  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    String dateStr = termNode.getTerm();
    String dateParsed;
    if (dateStr.length() > 10) {
      LocalDateTime localDateTime = LocalDateTime.parse(dateStr);
      dateParsed = localDateTime.toString();
    } else {
      LocalDate localDate = LocalDate.parse(dateStr);
      dateParsed = localDate.toString();
    }
    return getColumn() + handleOrderedRelation(termNode) + "'" + dateParsed + "'";
  }
}
