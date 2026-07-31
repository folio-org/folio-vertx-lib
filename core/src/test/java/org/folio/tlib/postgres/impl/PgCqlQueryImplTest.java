package org.folio.tlib.postgres.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.folio.tlib.postgres.PgCqlException;
import org.junit.jupiter.api.Test;
import org.z3950.zing.cql.CQLNode;

class PgCqlQueryImplTest {

  @Test
  void unknownNode() {
    var node = mock(CQLNode.class);
    var pgCqlQueryImpl = new PgCqlQueryImpl();
    var e = assertThrows(PgCqlException.class, () -> pgCqlQueryImpl.handleWhere(node));
    assertThat(e.getMessage(), is("Unsupported CQL construct: null"));
  }

}
