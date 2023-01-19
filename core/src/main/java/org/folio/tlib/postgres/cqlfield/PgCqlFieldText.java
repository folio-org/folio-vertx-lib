package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * <p>
 * Text fields always supports relation =, ==, &lt;>. By default
 * masking is enabled, but not allowed. The searches are exact match (case-sensitive).
 * </p>
 * <p>
 * There are two special cases for the empty term .
 * <ul>
 *   <li>field = "" matches all records where field is present (NOT NULL)</li>
 *   <li>field &lt;> matches all records where field is not present (NULL).</li>
 * </ul>
 * </p>
 * <p>
 *   Use {@link #withFullText()} to enable full-searches. Use {@link #withLikeOps()} to enable
 *   masking on exact searches.
 * </p>
 */
public class PgCqlFieldText extends PgCqlFieldBase implements PgCqlFieldType {
  private String language;

  private boolean enableLike;

  /**
   * Allow full-text search for field.
   * <p>This is triggered for relations "=", "adj", "all".
   * Relation "=" has same meaning as "adj" (except for empty term)
   * Full-text terms are case insensitive.
   * </p>
   *
   * @param language text-search configuration for PostgresQL
   * @return this.
   */
  public PgCqlFieldText withFullText(String language) {
    if (language == null) {
      throw new IllegalArgumentException("language must not be null");
    }
    this.language = language.replace("'", "''");
    return this;
  }

  /**
   * Allow full-text search for field using text-search configuration "simple".
   * <p>This is triggered for relations =, adj, all. Full-text terms are case insensitive</p>
   *
   * @return this.
   */
  public PgCqlFieldText withFullText() {
    return withFullText("simple");
  }

  /**
   * Allow masking for field.
   * <p>This is triggered for relations ==, <> when at least one of the masking operators
   * *, ? in CQL is used.
   * </p>
   *
   * @return this.
   */
  public PgCqlFieldText withLikeOps() {
    this.enableLike = true;
    return this;
  }

  static void unsupportedBackslashSequence(CQLTermNode termNode) {
    throw new IllegalArgumentException("Unsupported backslash sequence for: "
        + termNode.toCQL());
  }

  /**
   * CQL text term to PostgresSQL equality / inequality match.
   *
   * @param termNode which includes term and relation.
   * @return PostgresQL term
   */
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
            unsupportedBackslashSequence(termNode);
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
            pgTerm.append("''");
            break;
          default:
            pgTerm.append(c);
        }
        backslash = c == '\\';
      }
    }
    if (backslash) {
      unsupportedBackslashSequence(termNode);
    }
    return pgTerm.toString();
  }

  /**
   * CQL text term to Postgres term with LIKE operator.
   *
   * @param termNode which includes term and relation.
   * @param pgTerm   PostgresSQL term argument for LIKE upon completion.
   *                 Should be empty before this call.
   * @return true if LIKE must be used to honor masking operators.
   */
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
            pgTerm.append("\\\\");
            break;
          default:
            unsupportedBackslashSequence(termNode);
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
          case '_':
            pgTerm.append('\\');
            pgTerm.append(c);
            break;
          case '\'':
            pgTerm.append("''");
            break;
          default:
            pgTerm.append(c);
        }
        backslash = c == '\\';
      }
    }
    if (backslash) {
      unsupportedBackslashSequence(termNode);
    }
    return ops;
  }

  /**
   * CQL full text term to Postgres term.
   *
   * @param termNode which includes term and relation.
   * @return Postgres term.
   * @see <a href="https://www.postgresql.org/docs/13/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS">
   * String Constants section</a>
   *
   * <p>At this stage masking is unsupported and rejected.</p>
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
    String func = null;
    if (fulltext) {
      if ("adj".equals(base) || "=".equals(base)) {
        func = "phraseto_tsquery";
      } else if ("all".equals(base)) {
        func = "plainto_tsquery";
      }
    }
    if (func != null) {
      String pgTerm = cqlTermToPgTermFullText(termNode);
      return "to_tsvector('" + language + "', " + column + ") @@ " + func + "('"
          + language + "', '" + pgTerm + "')";
    }
    if (enableLike && ("=".equals(base) || "==".equals(base) || "<>".equals(base))) {
      StringBuilder cqlTerm = new StringBuilder();
      if (cqlTermToPgTermLike(termNode, cqlTerm)) {
        String op = "<>".equals(base) ? "NOT LIKE" : "LIKE";
        return column + " " + op + " '" + cqlTerm + "'";
      }
    }
    return column + " " + handleUnorderedRelation(termNode)
        + " '" + cqlTermToPgTermExact(termNode) + "'";
  }
}
