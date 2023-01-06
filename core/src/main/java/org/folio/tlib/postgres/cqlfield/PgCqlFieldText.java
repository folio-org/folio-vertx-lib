package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

public class PgCqlFieldText extends PgCqlFieldBase implements PgCqlFieldType {
  private String language;

  private boolean enableLike;

  public PgCqlFieldText withFullText(String language) {
    this.language = language;
    return this;
  }

  public PgCqlFieldText withFullText() {
    return withFullText("english");
  }

  public PgCqlFieldText withLikeOps() {
    this.enableLike = true;
    return this;
  }

  static boolean append(StringBuilder t, char c, boolean backslash) {
    if (c == '\\' && !backslash) {
      return true;
    }
    if (backslash && c == '\\') {
      t.append('\\');
    }
    if (c == '\'') {
      t.append(c);
    }
    t.append(c);
    return false;
  }

  static String cqlTermToPgTermExact(CQLTermNode termNode) {
    String cqlTerm = termNode.getTerm();
    StringBuilder pgTerm = new StringBuilder();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      backslash = append(pgTerm, cqlTerm.charAt(i), backslash);
    }
    return pgTerm.toString();
  }

  static boolean cqlTermToPgTermLike(CQLTermNode termNode, StringBuilder pgTerm) {
    boolean ops = false;
    String cqlTerm = termNode.getTerm();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      if (c == '*') {
        if (!backslash) {
          c = '%';
          ops = true;
        }
      } else if (c == '?') {
        if (!backslash) {
          c = '_';
          ops = true;
        }
      } else if (c == '_' || c == '%') {
        pgTerm.append('\\');
      }
      backslash = append(pgTerm, c, backslash);
    }
    return ops;
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
      // handle the CQL specials *, ?, ^, \\, rest are passed through
      if (c == '*') {
        if (!backslash) {
          throw new IllegalArgumentException("Masking op * unsupported for: " + termNode.toCQL());
        }
      } else if (c == '?') {
        if (!backslash) {
          throw new IllegalArgumentException("Masking op ? unsupported for: " + termNode.toCQL());
        }
      } else if (c == '^' && !backslash) {
        throw new IllegalArgumentException("Anchor op ^ unsupported for: " + termNode.toCQL());
      }
      backslash = append(pgTerm, c, backslash);
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
    if (enableLike && "=".equals(base)) {
      // not including "<>" as it is exact match, just like ==
      StringBuilder cqlTerm = new StringBuilder();
      if (cqlTermToPgTermLike(termNode, cqlTerm)) {
        return column + " LIKE '" + cqlTerm + "'";
      }
    }
    boolean fulltext = language != null;
    if (fulltext) {
      String func = null;
      if ("adj".equals(base) || "=".equals(base)) {
        func = "phraseto_tsquery";
      } else if ("all".equals(base)) {
        func = "plainto_tsquery";
      }
      if (func != null) {
        String pgTerm = cqlTermToPgTermFullText(termNode);
        return "to_tsvector('" + language + "', " + column + ") @@ " + func + "('"
            + language + "', '" + pgTerm + "')";
      }
    }
    return column + " " + handleUnorderedRelation(termNode)
        + " '" +  cqlTermToPgTermExact(termNode) + "'";
  }
}
