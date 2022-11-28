package org.folio.tlib.postgres.cqlfield;

import static org.folio.tlib.postgres.cqlfield.Util.basicOp;
import static org.folio.tlib.postgres.cqlfield.Util.handleNull;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldText implements PgCqlFieldType {
  String column;

  String language = "english";

  boolean fullText;

  public PgCqlFieldText(boolean fullText) {
    this.fullText = fullText;
  }

  public PgCqlFieldText(String language) {
    this.fullText = true;
    this.language = language;
  }

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
   * Convert CQL term to Postgres term - exact - without C style escapes in result.
   * <p> Double backslash is converted to backslash. Postgres quotes (') are escaped.
   * Otherwise things are passed through verbatim.
   * </p>
   * @param termNode termNode which includes term and relation.
   * @return Postgres term without C style escapes.
   */
  static String cqlTermToPgTermExact(CQLTermNode termNode) {
    String cqlTerm = termNode.getTerm();
    StringBuilder pgTerm = new StringBuilder();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      if (c == '\\' && backslash) {
        backslash = false;
      } else {
        pgTerm.append(c);
        if (c == '\'') {
          pgTerm.append('\''); // important to avoid SQL injection
        }
        backslash = c == '\\';
      }
    }
    return pgTerm.toString();
  }

  /**
   * CQL full text term to Postgres term.
   * @see <a href="https://www.postgresql.org/docs/13/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS">
   *   String Constants section</a>
   *
   * <p>At this stage masking is unsupported and rejected.</p>
   * @param termNode which includes term and relation.
   * @return Postgres term.
   */
  static String cqlTermToPgTermFullText(CQLTermNode termNode) {
    String cqlTerm = termNode.getTerm();
    StringBuilder pgTerm = new StringBuilder();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      // handle the CQL specials *, ?, ^, \\, rest are passed through as is
      if (c == '*') {
        if (!backslash) {
          throw new IllegalArgumentException("Masking op * unsupported for: " + termNode.toCQL());
        }
      } else if (c == '?') {
        if (!backslash) {
          throw new IllegalArgumentException("Masking op ? unsupported for: " + termNode.toCQL());
        }
      } else if (c == '^') {
        if (!backslash) {
          throw new IllegalArgumentException("Anchor op ^ unsupported for: " + termNode.toCQL());
        }
      } else if (c != '\\' && backslash) {
        pgTerm.append('\\');
      }
      if (c == '\\' && !backslash) {
        backslash = true;
      } else {
        pgTerm.append(c);
        if (c == '\'') {
          pgTerm.append(c);
        }
        backslash = false;
      }
    }
    return pgTerm.toString();
  }

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleNull(column, termNode);
    if (s != null) {
      return s;
    }
    String base = termNode.getRelation().getBase();
    boolean asFullText = fullText;
    if (asFullText) {
      asFullText = "=".equals(base) || "all".equals(base);
    }
    if (asFullText) {
      String pgTerm = cqlTermToPgTermFullText(termNode);
      return "to_tsvector('" + language + "', " + column + ") @@ plainto_tsquery('"
          + language + "', '" + pgTerm + "')";
    }
    return column + " " + basicOp(termNode)
        + " '" +  cqlTermToPgTermExact(termNode) + "'";
  }

}
