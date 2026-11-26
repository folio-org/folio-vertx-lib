package org.folio.tlib.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

class PgCqlExceptionTest {

  @Test
  void normalizesWhitespaceInFragment() {
    var term = new CQLTermNode("title", new CQLRelation("="), "a  b\tc\r\nd\ne\u2028f");

    assertEquals("Invalid term for: title = \"a b c d e f\"",
        new PgCqlException("Invalid term", term).getMessage());
  }

  @Test
  void limitsNormalizedFragmentTo80Characters() {
    String prefix = "title = \"a ";
    String filler = "x".repeat(79 - prefix.length());
    var term = new CQLTermNode("title", new CQLRelation("="), "a  " + filler + "😀more");

    assertEquals("Invalid term for: " + prefix + filler + "😀",
        new PgCqlException("Invalid term", term).getMessage());
  }
}
