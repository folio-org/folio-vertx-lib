package org.folio.tlib.postgres;

/**
 * Holder for SQL expressions parsed from a CQL query.
 */
public interface PgCqlQuery {
  /**
   * Parse CQL queries (combine with AND).
   * <p>Throws IllegalArgumentException on syntax error</p>
   *
   * @param query CQL query string.
   * @param q2 2nd CQL query string.
   */
  void parse(PgCqlDefinition definition, String query, String q2);

  /**
   * Get PostgresQL where clause (without WHERE).
   * <p>Throws IllegalArgumentException on syntax error</p>
   *
   * @return where clause argument or null if "always true" (WHERE can be omitted).
   */
  String getWhereClause();

  /**
   * Get PostgresQL where ORDER BY - (without ORDER BY).
   * <p>Throws IllegalArgumentException on syntax error</p>
   *
   * @return order by clause argument or null if no sorting (ORDER BY can be omitted)
   */
  String getOrderByClause();

  /**
   * Get PostgresQL where ORDER BY fields without asc/desc.
   * <p>Throws IllegalArgumentException on syntax error</p>
   *
   * @return order by clause argument or null if no sorting (ORDER BY can be omitted)
   */
  String getOrderByFields();
}
