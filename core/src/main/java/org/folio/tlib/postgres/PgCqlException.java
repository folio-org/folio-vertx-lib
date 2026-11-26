package org.folio.tlib.postgres;

import java.util.regex.Pattern;
import org.z3950.zing.cql.CQLTermNode;

/**
 * Thrown when processing CQL fails.
 */
public class PgCqlException extends RuntimeException {
  private static final int MAX_FRAGMENT_LENGTH = 80;
  private static final Pattern WHITESPACE =
      Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

  public PgCqlException(String msg) {
    super(msg);
  }

  public PgCqlException(String msg, CQLTermNode cqlTermNode) {
    super(msg + " for: " + formatFragment(cqlTermNode.toCQL()));
  }

  private static String formatFragment(String cql) {
    String fragment = WHITESPACE.matcher(cql).replaceAll(" ");
    int length = Math.min(MAX_FRAGMENT_LENGTH, fragment.codePointCount(0, fragment.length()));
    return fragment.substring(0, fragment.offsetByCodePoints(0, length));
  }
}
