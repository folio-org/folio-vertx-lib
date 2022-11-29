package org.folio.tlib.postgres.impl;

import java.util.HashMap;
import java.util.Map;
import org.folio.tlib.postgres.PgCqlDefinition;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.folio.tlib.postgres.PgCqlQuery;

public class PgCqlDefinitionImpl implements PgCqlDefinition {

  final Map<String, PgCqlFieldType> types = new HashMap<>();

  @Override
  public PgCqlDefinition addField(String name, PgCqlFieldType field) {
    // if column not specified, it defaults to CQL field name.
    if (field.getColumn() == null) {
      field.withColumn(name.toLowerCase());
    }
    types.put(name.toLowerCase(), field);
    return this;
  }

  @Override
  public PgCqlFieldType getFieldType(String name) {
    return types.get(name.toLowerCase());
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
