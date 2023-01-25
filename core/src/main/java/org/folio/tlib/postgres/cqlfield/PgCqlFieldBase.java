package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Base class for {@link PgCqlFieldType} implementations that handles column
 * and adds a number of utilities.
 */
public abstract class PgCqlFieldBase implements PgCqlFieldType {
  String column;

  @Override
  public String getColumn() {
    return column;
  }

  @Override
  public PgCqlFieldType withColumn(String column) {
    this.column = column;
    return this;
  }

  /**
   * If CQL term is empty, apply special semantics.
   * @param termNode CQL node.
   * @return SQL op rel value string; null if not handled.
   */
  public String handleEmptyTerm(CQLTermNode termNode) {
    if (!termNode.getTerm().isEmpty()) {
      return null;
    }
    String base = termNode.getRelation().getBase();
    switch (base) {
      case "=":
        return column + " IS NOT NULL";
      default:
        return null;
    }
  }

  /**
   * Return SQL for CQL unordered relation.
   * @param termNode CQL node.
   * @return SQL rel if handled.
   * @throws IllegalArgumentException for unsupported operator.
   */
  public String handleUnorderedRelation(CQLTermNode termNode) {
    String base = termNode.getRelation().getBase();
    switch (base) {
      case "==":
        return "=";
      case "=":
      case "<>":
        return base;
      default:
        throw new PgCqlException("Unsupported operator", termNode);
    }
  }

  /**
   * Return SQL for CQL ordered relation.
   * @param termNode CQL node.
   * @return SQL rel if handled.
   * @throws IllegalArgumentException for unsupported operator.
   */
  public String handleOrderedRelation(CQLTermNode termNode) {
    String base = termNode.getRelation().getBase();
    switch (base) {
      case "==":
        return "=";
      case "=":
      case "<>":
      case ">":
      case "<":
      case "<=":
      case ">=":
        return base;
      default:
        throw new PgCqlException("Unsupported operator", termNode);
    }
  }
}
