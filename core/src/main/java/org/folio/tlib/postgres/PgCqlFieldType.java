package org.folio.tlib.postgres;

import org.z3950.zing.cql.CQLTermNode;

public interface PgCqlFieldType {

   String getColumn();

   PgCqlFieldType withColumn(String column);

   String handleTermNode(CQLTermNode termNode);
}
