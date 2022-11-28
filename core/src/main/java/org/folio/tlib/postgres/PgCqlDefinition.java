package org.folio.tlib.postgres;

import org.folio.tlib.postgres.impl.PgCqlDefinitionImpl;

public interface PgCqlDefinition {

  static PgCqlDefinition create() {
    return new PgCqlDefinitionImpl();
  }

  /**
   * Add supported field.
   * @param field field.
   */
  PgCqlDefinition addField(String name, PgCqlFieldType field);

  /**
   * Get CQL field.
   * @param name field, such as "title"
   * @return field or null if not found
   */
  PgCqlFieldType getFieldType(String name);

  /**
   * Parse CQL query string.
   * <p>Throws IllegalArgumentException on syntax error</p>
   * @param query CQL query string; null for omitted CQL query.
   * @return query
   */
  PgCqlQuery parse(String query);

  /**
   * Parse CQL queries (combine with AND).
   * <p>Throws IllegalArgumentException on syntax error</p>
   * @param query CQL query string.
   * @param q2 2nd CQL query string.
   * @return query
   */
  PgCqlQuery parse(String query, String q2);

}
