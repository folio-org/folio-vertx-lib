package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldText extends PgCqlFieldBase implements PgCqlFieldType {
  private String language;

  PgCqlFieldText(String language) {
    this.language = language;
  }

  public PgCqlFieldText() {
    this(null);
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
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    String base = termNode.getRelation().getBase();
    boolean fulltext = language != null;
    if (fulltext) {
      // only for relations "=", "all" is full-text search performed
      fulltext = "=".equals(base) || "all".equals(base);
    }
    if (!fulltext) {
      return column + " " + handleUnoredredRelation(termNode)
          + " '" +  cqlTermToPgTermExact(termNode) + "'";
    }
    String pgTerm = cqlTermToPgTermFullText(termNode);
    return "to_tsvector('" + language + "', " + column + ") @@ plainto_tsquery('"
        + language + "', '" + pgTerm + "')";
  }

}
