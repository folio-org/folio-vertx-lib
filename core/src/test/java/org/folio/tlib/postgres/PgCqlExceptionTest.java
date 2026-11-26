package org.folio.tlib.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.z3950.zing.cql.CQLRelation;
import org.z3950.zing.cql.CQLTermNode;

class PgCqlExceptionTest {

  @Test
  void constructorWithTermPreservesBinaryCompatibility() {
    var term = new CQLTermNode("title", new CQLRelation("="), "example");
    var exception = new PgCqlException("Invalid term", term);

    assertEquals("Invalid term", exception.getMessage());
  }
}
