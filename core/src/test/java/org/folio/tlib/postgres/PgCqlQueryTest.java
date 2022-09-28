package org.folio.tlib.postgres;

import io.vertx.sqlclient.Tuple;
import org.junit.Assert;
import org.junit.Test;

public class PgCqlQueryTest {

  @Test
  public void testSimple() {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(null);
    Tuple tuple = Tuple.tuple();
    Assert.assertNull(pgCqlQuery.getWhereClause(tuple));
    Assert.assertNull(pgCqlQuery.getOrderByClause());
    Assert.assertEquals(0, tuple.size());

    pgCqlDefinition.addField(new PgCqlField("title", "dc.title", PgCqlField.Type.TEXT));

    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse("dc.Title==value");
    Assert.assertEquals("title = $1", pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value", tuple.get(String.class, 0));

    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse(null, "dc.Title==value2 OR dc.title==value3");
    Assert.assertEquals("(title = $1 OR title = $2)",
        pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value2", tuple.get(String.class, 0));
    Assert.assertEquals("value3", tuple.get(String.class, 1));

    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse("dc.Title==value1", "dc.Title==value2 OR dc.title==value3");
    Assert.assertEquals("(title = $1 AND (title = $2 OR title = $3))",
        pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value1", tuple.get(String.class, 0));
    Assert.assertEquals("value2", tuple.get(String.class, 1));
    Assert.assertEquals("value3", tuple.get(String.class, 2));

    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse("dc.Title==value1 sortby title", "dc.Title==value2 OR dc.title==value3");
    Assert.assertEquals("(title = $1 AND (title = $2 OR title = $3))",
        pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value1", tuple.get(String.class, 0));
    Assert.assertEquals("value2", tuple.get(String.class, 1));
    Assert.assertEquals("value3", tuple.get(String.class, 2));

    pgCqlDefinition.addField(new PgCqlField("cql.allRecords", PgCqlField.Type.ALWAYS_MATCHES));
    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse("cql.allRecords = 1", "dc.title==value1");
    Assert.assertEquals("title = $1", pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value1", tuple.get(String.class, 0));

    tuple = Tuple.tuple();
    pgCqlQuery = pgCqlDefinition.parse("cql.allRecords = 1 sortby title", "dc.title==value1");
    Assert.assertEquals("title = $1", pgCqlQuery.getWhereClause(tuple));
    Assert.assertEquals("value1", tuple.get(String.class, 0));
  }

  static String ftResponse(String column, String term) {
    return "to_tsvector('english', " + column + ") @@ plainto_tsquery('english', " + term + ")";
  }

  @Test
  public void testQueries() {
    Object[][] list = new String[][] {
        { "(", "error: expected index or term, got EOF" },
        { "foo=bar", "error: Unsupported CQL index: foo" },
        { "Title=v1", ftResponse("title", "$1"), "v1" },
        { "Title=v1 or Title=v2", "(" + ftResponse("title", "$1") + " OR "
            + ftResponse("title", "$2") + ")", "v1", "v2" },
        { "Title all v1", ftResponse("title", "$1"), "v1" },
        { "Title>v1", "error: Unsupported operator > for: Title > v1" },
        { "Title=\"men's room\"", ftResponse("title", "$1"), "men's room" },
        { "Title=men's room", ftResponse("title", "$1"), "men's room" },
        { "Title=v1*", "error: Masking op * unsupported for: Title = v1*" },
        { "Title=v1?", "error: Masking op ? unsupported for: Title = v1?" },
        { "Title=v1^", "error: Anchor op ^ unsupported for: Title = v1^" },
        { "Title=a\\*b", ftResponse("title", "$1"), "a*b" },
        { "Title=a\\^b", ftResponse("title", "$1"), "a^b" },
        { "Title=a\\?b", ftResponse("title", "$1"), "a?b" },
        { "Title=a\\?b", ftResponse("title", "$1"), "a?b" },
        { "Title=a\\n", ftResponse("title", "$1"), "a\\n" },
        { "Title=\"a\\\"\"", ftResponse("title", "$1"), "a\"" },
        { "Title=\"a\\\"b\"", ftResponse("title", "$1"), "a\"b" },
        { "Title=a\\12", ftResponse("title", "$1"), "a\\12" },
        { "Title=a\\\\", ftResponse("title", "$1"), "a\\" },
        { "Title=a\\'", ftResponse("title", "$1"), "a\\'" },
        { "Title=a\\'b", ftResponse("title", "$1"), "a\\'b" },
        { "Title=a\\\\\\n", ftResponse("title", "$1"), "a\\\\n" },
        { "Title=a\\\\", ftResponse("title", "$1"), "a\\" },
        { "Title=aa\\\\1", ftResponse("title", "$1"), "aa\\1" },
        { "Title=ab\\\\\\?", ftResponse("title", "$1"), "ab\\?" },
        { "Title=\"b\\\\\"", ftResponse("title", "$1"), "b\\" },
        { "Title=\"c\\\\'\"", ftResponse("title", "$1"), "c\\'" },
        { "Title=\"c\\\\d\"", ftResponse("title", "$1"), "c\\d" },
        { "Title=\"d\\\\\\\\\"", ftResponse("title", "$1"), "d\\\\" },
        { "Title=\"x\\\\\\\"\\\\\"", ftResponse("title", "$1"), "x\\\"\\" },
        { "Title=\"\"", "title IS NOT NULL" },
        { "Title<>\"\"", "title IS NULL" },
        { "Title==\"\"", "title = $1", "" },
        { "Title==\"*?^\"", "title = $1", "*?^" },
        { "Title==\"\\*\\?\\^\"", "title = $1", "\\*\\?\\^" },
        { "Title==\"b\\\\\"", "title = $1", "b\\" },
        { "Title==\"c\\\\'\"", "title = $1", "c\\'" },
        { "Title==\"d\\\\\\\\\"", "title = $1", "d\\\\" },
        { "Title==\"e\\\\\\\"\\\\\"", "title = $1", "e\\\"\\" },
        { "Title>\"\"", "error: Unsupported operator > for: Title > \"\"" },
        { "Title==v1 or title==v2",  "(title = $1 OR title = $2)", "v1", "v2"},
        { "isbn=978-3-16-148410-0", "isbn = $1", "978-3-16-148410-0" },
        { "isbn=978-3-16-148410-*", "isbn = $1", "978-3-16-148410-*" },
        { "cql.allRecords=1 or title==v1", null, "v1" }, // TODO: should be empty tuple
        { "title==v1 or cql.allRecords=1", null, "v1" }, // TODO: should be empty tuple
        { "Title==v1 and title==v2", "(title = $1 AND title = $2)", "v1", "v2" },
        { "Title==v1 and cql.allRecords=1", "title = $1", "v1" },
        { "cql.allRecords=1 and Title==v2", "title = $1", "v2" },
        { "Title==v1 not title==v2", "(title = $1 AND NOT title = $2)", "v1", "v2" },
        { "cql.allRecords=1 not title==v2", "NOT (title = $1)", "v2" },
        { "title==v1 not cql.allRecords=1", "FALSE", "v1" }, // TODO: should be empty tuple
        { "title==v1 prox title==v2", "error: Unsupported operator PROX" },
        { "cost=1 or cost=2 and cost=3", "((cost=1 OR cost=2) AND cost=3)" }, // boolean are left-assoc and same precedence in CQL
        { "cost=1 or (cost=2 and cost=3)", "(cost=1 OR (cost=2 AND cost=3))" },
        { "cost=\"\" or cost<>\"\" not cost<>\"\"", "((cost IS NOT NULL OR cost IS NULL) AND NOT cost IS NULL)" },
        { "cost=1", "cost=1" },
        { "cost=+1.9", "cost=+1.9" },
        { "cost=e", "cost=e" },
        { "cost=1.5e3", "cost=1.5e3" },
        { "cost=-1,90", "error: Bad numeric for: cost = -1,90" },
        { "cost=0x100", "error: Bad numeric for: cost = 0x100" },
        { "cost==\"\"", "error: Bad numeric for: cost == \"\"" },
        { "cost>1", "cost>1" },
        { "cost>=2", "cost>=2" },
        { "cost==3", "cost=3" },
        { "cost<>4", "cost<>4" },
        { "cost<5", "cost<5" },
        { "cost<=6", "cost<=6" },
        { "cost adj 7", "error: Unsupported operator adj for: cost adj 7" },
        { "cost=\"\"", "cost IS NOT NULL" },
        { "paid=true", "paid=TRUE" },
        { "paid=False", "paid=FALSE" },
        { "paid=fals", "error: Bad boolean for: paid = fals" },
        { "paid=\"\"", "paid IS NOT NULL" },
        { "paid==\"\"", "error: Bad boolean for: paid == \"\"" },
        { "id=null", "error: Invalid UUID in id = null" },
        { "id==\"\"", "error: Invalid UUID in id == \"\"" },
        { "id=\"\"", "id IS NOT NULL" },
        { "id=6736bd11-5073-4026-81b5-b70b24179e02", "id='6736bd11-5073-4026-81b5-b70b24179e02'" },
        { "id=6736BD11-5073-4026-81B5-B70B24179E02", "id='6736bd11-5073-4026-81b5-b70b24179e02'" },
        { "id<>6736bd11-5073-4026-81b5-b70b24179e02", "id<>'6736bd11-5073-4026-81b5-b70b24179e02'" },
        { "title==v1 sortby cost", "title = $1", "v1"},
        { ">x = \"http://foo.org/p\" title==v1", "title = $1", "v1"},
    };
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField(new PgCqlField("cql.allRecords", PgCqlField.Type.ALWAYS_MATCHES));
    pgCqlDefinition.addField(new PgCqlField("title", PgCqlField.Type.FULLTEXT));
    pgCqlDefinition.addField(new PgCqlField("isbn", PgCqlField.Type.TEXT));
    pgCqlDefinition.addField(new PgCqlField("cost", PgCqlField.Type.NUMBER));
    pgCqlDefinition.addField(new PgCqlField("paid", PgCqlField.Type.BOOLEAN));
    pgCqlDefinition.addField(new PgCqlField("id", PgCqlField.Type.UUID));
    for (Object [] entry : list) {
      String query = (String) entry[0];
      String expect = (String) entry[1];
      try {
        PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query);
        Tuple tuple = Tuple.tuple();
        Assert.assertEquals("CQL: " + query, expect, pgCqlQuery.getWhereClause(tuple));
        Assert.assertEquals("CQL: " + query, entry.length - 2, tuple.size());
        for (int j = 0; j < tuple.size(); j++) {
          Assert.assertEquals("CQL: " + query, entry[j + 2], tuple.getString(j));
        }
      } catch (IllegalArgumentException e) {
        Assert.assertEquals(expect, "error: " + e.getMessage());
      }
    }
  }

  @Test
  public void testSort() {
    String[][] list = new String[][]{
        {"isbn=1234 sortby foo", "error: Unsupported CQL index: foo", null},
        {"paid=1234", null, null},
        {"paid=1234 sortby isbn/xx", "error: Unsupported sort modifier: xx", null},
        {"paid=1234 sortby isbn", "isbn ASC", "isbn"},
        {">dc=\"http://foo.org/p\" paid=1234 sortby isbn", "isbn ASC", "isbn"},
        {"paid=1234 sortby cost/sort.descending title/sort.ascending", "cost DESC, title ASC", "cost, title"},
    };

    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField(new PgCqlField("cql.allRecords", PgCqlField.Type.ALWAYS_MATCHES));
    pgCqlDefinition.addField(new PgCqlField("title", PgCqlField.Type.FULLTEXT));
    pgCqlDefinition.addField(new PgCqlField("isbn", PgCqlField.Type.TEXT));
    pgCqlDefinition.addField(new PgCqlField("cost", PgCqlField.Type.NUMBER));
    pgCqlDefinition.addField(new PgCqlField("paid", PgCqlField.Type.BOOLEAN));
    pgCqlDefinition.addField(new PgCqlField("id", PgCqlField.Type.UUID));
    for (String [] entry : list) {
      String query = entry[0];
      String expect = entry[1];
      String fields = entry[2];
      try {
        PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query);
        Assert.assertEquals("CQL: " + query, expect, pgCqlQuery.getOrderByClause());
        Assert.assertEquals("CQL: " + query, fields, pgCqlQuery.getOrderByFields());
      } catch (IllegalArgumentException e) {
        Assert.assertEquals(expect, "error: " + e.getMessage());
      }
    }

  }

}
