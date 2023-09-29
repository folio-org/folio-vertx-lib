package org.folio.tlib.postgres.cqlfield;

import org.folio.tlib.postgres.PgCqlException;
import org.folio.tlib.postgres.PgCqlFieldType;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Create field for searching relations of type TEXT / VARCHAR / CHAR.
 *
 * <p>Use one or more of
 * <ul>
 *   <li>{@link #withFullText()} to enable full-text searches (masking unsupported).</li>
 *   <li>{@link #withExact()} ()} to enable exact match searches (masking unsupported)</li>
 *   <li>{@link #withLikeOps()} to enable exact match searches with masking operators</li>
 * </ul>
 */
public class PgCqlFieldText extends PgCqlFieldBase implements PgCqlFieldType {

  private String language;

  private boolean enableLike;

  private boolean enableExact;

  /**
   * Allow full-text search for field.
   * <p>This is triggered for relations "=", "adj", "all".
   * Relation "=" has same meaning as "adj" (except for empty term)
   * Full-text terms are case insensitive.
   * </p><p>
   * An index of type GIN should be created for the relation. See
   * <a href="https://www.postgresql.org/docs/current/textsearch-tables.html">Tables and Indexes</a>.
   * </p>
   *
   * @param language text-search configuration for PostgresQL
   * @return this.
   */
  public PgCqlFieldText withFullText(String language) {
    if (language == null) {
      throw new PgCqlException("language must not be null");
    }
    this.language = language.replace("'", "''");
    return this;
  }

  /**
   * Allow full-text search for field using text-search configuration "simple".
   * <p>This is triggered for relations =, adj, all.
   * Full-text terms are case insensitive.
   * Operator = with the empty string is not allowed unless
   * withExact or withLikeOps is specified in which case it maps to <code>IS NOT NULL</code>.
   * </p><p>
   * A GIN index should be created for the relation. See
   * <a href="https://www.postgresql.org/docs/current/textsearch-tables.html">Tables and Indexes</a>.
   * </p>
   *
   * @return this.
   */
  public PgCqlFieldText withFullText() {
    return withFullText("simple");
  }

  /**
   * Allow masking for field.
   * <p>This is triggered for relations {@code ==}, {@code <>} when at least one of the masking
   * operators {@code *}, {@code ?} in CQL is used and is converted into a <code>SQL LIKE</code>
   * search with {@code %}, {@code _}.
   * </p><p>
   * A B-Tree index with operator class <code>text_pattern_ops</code> should be
   * created for the relation.
   * </p>
   *
   * @return this.
   */
  public PgCqlFieldText withLikeOps() {
    this.enableExact = this.enableLike = true;
    return this;
  }

  /**
   * Allow exact searches.
   * <p>This is triggered for relations {@code ==}, {@code <>}. It also allows {@code =} with
   * empty string which maps to <code>IS NOT NULL</code>.
   * To search for empty string, operator {@code ==} must be used.
   * </p><p>
   * A B-Tree index should be created for the relation.
   * </p>
   *
   * @return this.
   */
  public PgCqlFieldText withExact() {
    this.enableExact = true;
    return this;
  }

  /**
   * CQL text term to PostgresSQL equality / inequality match.
   * <p>Unrecognized backslash sequences are treated as errors; see
   * <a href="https://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html#_Toc235849921">B3.3 Masking</a>
   * of the CQL standard.
   * </p>
   *
   * @param termNode which includes term and relation.
   * @return PostgresQL term
   * @see <a href="https://www.postgresql.org/docs/13/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS">
   * String Constants section</a>
   */
  static String maskedExact(CQLTermNode termNode) {
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
            throw new PgCqlException("A masking backslash in a CQL string must be followed by"
                + " *, ?, ^, \" or \\", termNode);
        }
        backslash = false;
      } else {
        switch (c) {
          case '*':
            throw new PgCqlException("Masking op * unsupported", termNode);
          case '?':
            throw new PgCqlException("Masking op ? unsupported", termNode);
          case '^':
            throw new PgCqlException("Anchor op ^ unsupported", termNode);
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
      throw new PgCqlException("A CQL string must not end with a masking backslash", termNode);
    }
    return pgTerm.toString();
  }

  /**
   * CQL masked text term to Postgres term with LIKE operator.
   * <p>Unrecognized backslash sequences are treated as errors; see
   * <a href="https://docs.oasis-open.org/search-ws/searchRetrieve/v1.0/os/part5-cql/searchRetrieve-v1.0-os-part5-cql.html#_Toc235849921">B3.3 Masking</a>
   * of the CQL standard.
   * </p>
   *
   * @param termNode which includes term and relation.
   * @param pgTerm   PostgresSQL term argument for LIKE upon completion.
   *                 Should be empty before this call.
   * @return true if LIKE must be used to honor masking operators.
   */
  static boolean maskedLike(CQLTermNode termNode, StringBuilder pgTerm) {
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
            throw new PgCqlException("A masking backslash in a CQL string must be followed by"
                + " *, ?, ^, \" or \\", termNode);
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
            throw new PgCqlException("Anchor op ^ unsupported", termNode);
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
      throw new PgCqlException("A CQL string must not end with a masking backslash", termNode);
    }
    return ops;
  }

  /**
   * CQL masked full text term to Postgres term.
   *
   * <p>At this stage masking is unsupported and rejected.</p>
   *
   * @param termNode which includes term and relation.
   * @return Postgres term.
   * @see <a href="https://www.postgresql.org/docs/13/sql-syntax-lexical.html#SQL-SYNTAX-STRINGS">
   * String Constants section</a>
   */
  static String maskedFulltext(CQLTermNode termNode) {
    return maskedExact(termNode);
  }

  @Override
  public String handleTermNode(CQLTermNode termNode) {
    String s = handleEmptyTerm(termNode);
    if (s != null) {
      if (!enableExact) {
        throw new PgCqlException("= \"\" (not null test) is not supported", termNode);
      }
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
      String pgTerm = maskedFulltext(termNode);
      return "to_tsvector('" + language + "', " + column + ") @@ " + func + "('"
          + language + "', '" + pgTerm + "')";
    }
    if (!enableExact) {
      throw new PgCqlException("Unsupported operator", termNode);
    }
    if (enableLike && ("=".equals(base) || "==".equals(base) || "<>".equals(base))) {
      StringBuilder cqlTerm = new StringBuilder();
      if (maskedLike(termNode, cqlTerm)) {
        String op = "<>".equals(base) ? "NOT LIKE" : "LIKE";
        return column + " " + op + " '" + cqlTerm + "'";
      }
    }
    return column + " " + handleUnorderedRelation(termNode)
        + " '" + maskedExact(termNode) + "'";
  }
}
