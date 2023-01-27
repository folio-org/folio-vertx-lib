package org.folio.tlib.postgres.impl;

import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.tlib.postgres.PgCqlDefinition;
import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.folio.tlib.postgres.PgCqlQuery;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParseException;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLPrefixNode;
import org.z3950.zing.cql.CQLSortNode;
import org.z3950.zing.cql.CQLTermNode;
import org.z3950.zing.cql.Modifier;
import org.z3950.zing.cql.ModifierSet;

public class PgCqlQueryImpl implements PgCqlQuery {
  private static final Logger log = LogManager.getLogger(PgCqlQueryImpl.class);

  final CQLParser parser = new CQLParser(CQLParser.V1POINT2);

  CQLNode cqlNodeRoot;

  PgCqlDefinition pgCqlDefinition;

  @Override
  public void parse(PgCqlDefinition definition, String query, String q2) {
    String resultingQuery;

    pgCqlDefinition = definition;
    try {
      if (query == null && q2 == null) {
        cqlNodeRoot = null;
        return;
      }
      if (query != null && q2 != null) {
        // get rid of sortby as it can't be combined and we don't
        // it for sorting anyway.
        CQLNode node = parser.parse(query);
        if (node instanceof CQLSortNode) {
          CQLSortNode cqlSortNode = (CQLSortNode) node;
          node = cqlSortNode.getSubtree();
        }
        resultingQuery = "(" + node.toCQL() + ") AND (" + q2 + ")";
      } else if (query != null) {
        resultingQuery = query;
      } else {
        resultingQuery = q2;
      }
      log.debug("Parsing {}", resultingQuery);
      cqlNodeRoot = parser.parse(resultingQuery);
    } catch (CQLParseException | IOException e) {
      throw new PgCqlException(e.getMessage());
    }
  }

  @Override
  public String getWhereClause() {
    return handleWhere(cqlNodeRoot);
  }

  @Override
  public String getOrderByClause() {
    return handleOrderBy(cqlNodeRoot, true);
  }

  @Override
  public String getOrderByFields() {
    return handleOrderBy(cqlNodeRoot, false);
  }

  String handleWhere(CQLNode node) {
    if (node == null) {
      return null;
    }
    if (node instanceof CQLBooleanNode) {
      CQLBooleanNode booleanNode = (CQLBooleanNode) node;
      String left = handleWhere(booleanNode.getLeftOperand());
      String right = handleWhere(booleanNode.getRightOperand());
      switch (booleanNode.getOperator()) {
        case OR:
          if (right != null && left != null) {
            return "(" + left + " OR " + right + ")";
          }
          return null;
        case AND:
          if (right != null && left != null) {
            return "(" + left + " AND " + right + ")";
          } else if (right != null) {
            return right;
          } else {
            return left;
          }
        case NOT:
          if (right != null && left != null) {
            return "(" + left + " AND NOT " + right + ")";
          } else if (right != null) {
            return "NOT (" + right + ")";
          }
          return "FALSE";
        default:
          throw new PgCqlException("Unsupported operator "
              + booleanNode.getOperator().name());
      }
    } else if (node instanceof CQLTermNode) {
      CQLTermNode termNode = (CQLTermNode) node;
      PgCqlFieldType type = pgCqlDefinition.getFieldType(termNode.getIndex());
      if (type == null) {
        throw new PgCqlException("Unsupported CQL index: " + termNode.getIndex());
      }
      return type.handleTermNode(termNode);
    } else if (node instanceof CQLSortNode) {
      CQLSortNode sortNode = (CQLSortNode) node;
      return handleWhere(sortNode.getSubtree());
    } else if (node instanceof CQLPrefixNode) {
      CQLPrefixNode prefixNode = (CQLPrefixNode) node;
      return handleWhere(prefixNode.getSubtree());
    }
    // other node types unsupported, for example proximity
    throw new PgCqlException("Unsupported CQL construct: " + node.toCQL());
  }

  String handleOrderBy(CQLNode node, boolean includeOps) {
    if (node == null) {
      return null;
    }
    if (node instanceof CQLSortNode) {
      StringBuilder res = new StringBuilder();
      CQLSortNode sortNode = (CQLSortNode) node;
      for (ModifierSet modifierSet: sortNode.getSortIndexes()) {
        if (res.length() > 0) {
          res.append(", ");
        }
        PgCqlFieldType type = pgCqlDefinition.getFieldType(modifierSet.getBase());
        if (type == null) {
          throw new PgCqlException("Unsupported CQL index: " + modifierSet.getBase());
        }
        res.append(type.getColumn());
        if (includeOps) {
          res.append(" ");
          String desc = "ASC";
          for (Modifier modifier : modifierSet.getModifiers()) {
            switch (modifier.getType()) {
              case "sort.ascending":
                break;
              case "sort.descending":
                desc = "DESC";
                break;
              default:
                throw new PgCqlException("Unsupported sort modifier: "
                    + modifier.getType());
            }
          }
          res.append(desc);
        }
      }
      return res.toString();
    } else if (node instanceof CQLPrefixNode) {
      CQLPrefixNode prefixNode = (CQLPrefixNode) node;
      return handleOrderBy(prefixNode.getSubtree(), includeOps);
    } else {
      return null;
    }
  }
}
