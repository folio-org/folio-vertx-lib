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
    return withFullText("simple");
  }

  public PgCqlFieldText withLikeOps() {
    this.enableLike = true;
    return this;
  }

  static String cqlTermToPgTermExact(CQLTermNode termNode) {
    String cqlTerm = termNode.getTerm();
    StringBuilder pgTerm = new StringBuilder();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      if (backslash) {
        switch (c) {
          case '*':
          case '\"':
          case '?':
          case '^':
          case '\\':
            pgTerm.append(c);
            break;
          default:
            throw new IllegalArgumentException("Unsupported backslash sequence for: "
                + termNode.toCQL());
        }
        backslash = false;
      } else {
        switch (c) {
          case '*':
            throw new IllegalArgumentException("Masking op * unsupported for: " + termNode.toCQL());
          case '?':
            throw new IllegalArgumentException("Masking op ? unsupported for: " + termNode.toCQL());
          case '^':
            throw new IllegalArgumentException("Anchor op ^ unsupported for: " + termNode.toCQL());
          case '\\':
            break;
          case '\'':
            pgTerm.append(c);
            pgTerm.append(c);
            break;
          default:
            pgTerm.append(c);
        }
        backslash = c == '\\';
      }
    }
    if (backslash) {
      throw new IllegalArgumentException("Unsupported backslash sequence for: " + termNode.toCQL());
    }
    return pgTerm.toString();
  }

  static boolean cqlTermToPgTermLike(CQLTermNode termNode, StringBuilder pgTerm) {
    boolean ops = false;
    String cqlTerm = termNode.getTerm();
    boolean backslash = false;
    for (int i = 0; i < cqlTerm.length(); i++) {
      char c = cqlTerm.charAt(i);
      if (backslash) {
        switch (c) {
          case '*':
          case '?':
          case '^':
          case '\"':
            pgTerm.append(c);
            break;
          case '\\':
            pgTerm.append(c);
            pgTerm.append(c);
            break;
          default:
            throw new IllegalArgumentException("Unsupported backslash sequence for: "
                + termNode.toCQL());
        }
        backslash = false;
      } else {
        switch (c) {
          case '*':
            pgTerm.append('%');
            ops = true;
            break;
          case '?':
            pgTerm.append('_');
            ops = true;
            break;
          case '^':
            if (i != 0 && i != cqlTerm.length() - 1) {
              throw new IllegalArgumentException("Anchor op ^ unsupported for: "
                  + termNode.toCQL());
            }
            break;
          case '\\':
            break;
          case '%':
            pgTerm.append('\\');
            pgTerm.append(c);
            break;
          case '_':
            pgTerm.append('\\');
            pgTerm.append(c);
            break;
          case '\'':
            pgTerm.append(c);
            pgTerm.append(c);
            break;
          default:
            pgTerm.append(c);
        }
        backslash = c == '\\';
      }
    }
    if (backslash) {
      throw new IllegalArgumentException("Unsupported backslash sequence for: " + termNode.toCQL());
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
    return cqlTermToPgTermExact(termNode);
  }

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      return s;
    }
    boolean fulltext = language != null;
    String base = termNode.getRelation().getBase();
    if ("<>".equals(base) || "==".equals(base) || ("=".equals(base) && !fulltext)) {
      if (enableLike) {
        StringBuilder cqlTerm = new StringBuilder();
        if (cqlTermToPgTermLike(termNode, cqlTerm)) {
          if (!enableLike) {
            throw new IllegalArgumentException("Masking unsupported " + base + " for: "
                + termNode.toCQL());
          }
          String op = "<>".equals(base) ? "NOT LIKE" : "LIKE";
          return column + " " + op + " '" + cqlTerm + "'";
        }
      }
      return column + " " + handleUnorderedRelation(termNode)
          + " '" + cqlTermToPgTermExact(termNode) + "'";
    }
    String func = null;
    if (fulltext) {
      if ("adj".equals(base) || "=".equals(base)) {
        func = "phraseto_tsquery";
      } else if ("all".equals(base)) {
        func = "plainto_tsquery";
      }
    }
    if (func == null) {
      throw new IllegalArgumentException("Unsupported operator " + base + " for: "
          + termNode.toCQL());
    }
    String pgTerm = cqlTermToPgTermFullText(termNode);
    return "to_tsvector('" + language + "', " + column + ") @@ " + func + "('"
        + language + "', '" + pgTerm + "')";
  }
}
