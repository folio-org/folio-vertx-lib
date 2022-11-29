package org.folio.tlib.postgres;

import org.z3950.zing.cql.CQLTermNode;

public interface PgCqlFieldType {

  /**
   * Gets column for this field type handler.
   * @return column string
   */
  String getColumn();

  /**
   * Sets the column that this field type handler is using.
   * @param column SQL column
   * @return this
   */
  PgCqlFieldType withColumn(String column);

  /** Return SQL for equivalent of CQL "field relation term".
   *
   * <p>
   * The field is not known, but the equivalent column is
   * set with {@link #withColumn}. Get relation by using {@link CQLTermNode#getRelation()}.
   * Get term by using {@link CQLTermNode#getTerm()}.
   * </p>
   * @param termNode for the "field relation".
   * @return SQL string.
   */
  String handleTermNode(CQLTermNode termNode);
}
