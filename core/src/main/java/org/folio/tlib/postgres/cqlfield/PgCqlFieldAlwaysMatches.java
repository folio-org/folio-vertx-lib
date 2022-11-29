package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Special field that does not limit the search (always matches).
 *
 * <pre>{@code
 *   pgCqlDefinition = PgCqlDefinition.create();
 *   pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches())
 * }</pre>
 *
 */
public class PgCqlFieldAlwaysMatches extends PgCqlFieldBase implements PgCqlFieldType {
  @Override
  public String handleTermNode(CQLTermNode termNode) {
    return null;
  }
}
