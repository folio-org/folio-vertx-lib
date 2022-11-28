package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;

public class PgCqlFieldFullText extends PgCqlFieldText implements PgCqlFieldType {

  public PgCqlFieldFullText() {
    this("english");
  }

  public PgCqlFieldFullText(String language) {
    super(language);
  }

}
