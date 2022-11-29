package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;

/**
 * PostgresQL full text field type.
 *
 * <pre>{@code
 *   pgCqlDefinition = PgCqlDefinition.create();
 *   pgCqlDefinition.addField("title", new PgCqlFieldFullText("english"))
 * }</pre>
 */
public class PgCqlFieldFullText extends PgCqlFieldText implements PgCqlFieldType {

  /**
   * Create with language "english".
   */
  public PgCqlFieldFullText() {
    this("english");
  }

  /**
   * Create with language.
   * @param language argument used for Postgresql's to_tsvector, to_tsquery.
   */
  public PgCqlFieldFullText(String language) {
    super(language);
  }

}
