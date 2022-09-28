package org.folio.tlib.postgres.impl;

import java.util.HashMap;
import java.util.Map;
import org.folio.tlib.postgres.PgCqlDefinition;
import org.folio.tlib.postgres.PgCqlField;
import org.folio.tlib.postgres.PgCqlQuery;

public class PgCqlDefinitionImpl implements PgCqlDefinition {

  final Map<String, PgCqlField> fields = new HashMap<>();

  @Override
  public PgCqlDefinition addField(PgCqlField field) {
    fields.put(field.getName().toLowerCase(), field);
    return this;
  }

  @Override
  public PgCqlField getField(String name) {
    return fields.get(name.toLowerCase());
  }

  @Override
  public PgCqlQuery parse(String query) {
    return parse(query, null);
  }

  @Override
  public PgCqlQuery parse(String query, String q2) {
    PgCqlQuery pgCqlQuery = new PgCqlQueryImpl();
    pgCqlQuery.parse(this, query, q2);
    return pgCqlQuery;
  }
}
