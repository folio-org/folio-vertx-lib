package org.folio.tlib.postgres;

import java.time.format.DateTimeParseException;
import java.util.stream.Stream;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldAlwaysMatches;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldBoolean;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldNumber;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldUuid;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldTimestamp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PgCqlQueryTest {

  static Stream<Arguments> cql2ComboQueries() {
    return Stream.of(
        Arguments.of(null, null, null),
        Arguments.of("dc.Title==value", null, "title = 'value'"),
        Arguments.of(null, "dc.Title==value", "title = 'value'"),
        Arguments.of("dc.Title==value", "cql.allRecords=1", "title = 'value'"),
        Arguments.of("cql.allRecords=1", "dc.Title==value", "title = 'value'"),
        Arguments.of(null, "dc.Title==value2 OR dc.title==value3", "(title = 'value2' OR title = 'value3')"),
        Arguments.of("dc.Title==value1", "dc.Title==value2 OR dc.title==value3",
            "(title = 'value1' AND (title = 'value2' OR title = 'value3'))"),
        Arguments.of("dc.Title==value1 sortby title", "dc.Title==value2 OR dc.title==value3",
            "(title = 'value1' AND (title = 'value2' OR title = 'value3'))"),
        Arguments.of("cql.allRecords = 1", "dc.title==value1", "title = 'value1'"),
        Arguments.of("cql.allRecords = 1 sortby title", "dc.title==value1", "title = 'value1'")
    );
  }

  @ParameterizedTest
  @MethodSource("cql2ComboQueries")
  void testCqlQueries(String query1, String query2, String expect) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("dc.title", new PgCqlFieldText().withExact().withColumn("title"));
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query1, query2);
    assertThat(pgCqlQuery.getWhereClause(), is(expect));
  }

  @Test
  void withFullTextNull() {
    PgCqlFieldText pgCqlFieldText = new PgCqlFieldText();
    Assertions.assertThrows(PgCqlException.class, () -> pgCqlFieldText.withFullText(null));
  }

  static String ftResponseAdj(String column, String term) {
    return ftResponse(column, term, "phraseto_tsquery", "simple");
  }

  static String ftResponseAll(String column, String term) {
    return ftResponse(column, term, "plainto_tsquery", "simple");
  }

  static String ftResponse(String column, String term, String func, String language) {
    return "to_tsvector('" + language + "', " + column
        + ") @@ " + func + "('" + language + "', '" + term + "')";
  }

  static Stream<Arguments> cqlQueries() {
    return Stream.of(
        Arguments.of( "foo >", "error: expected index or term, got EOF" ),
        Arguments.of( "foo=bar", "error: Unsupported CQL index: foo" ),
        Arguments.of( "Title=v1", ftResponseAdj("title", "v1") ),
        Arguments.of( "Title all v1", ftResponseAll("title", "v1") ),
        Arguments.of( "Title adj v1", ftResponseAdj("title", "v1") ),
        Arguments.of( "Title>v1", "error: Unsupported operator for: Title > v1" ),
        Arguments.of( "Title=\"men's room\"", ftResponseAdj("title", "men''s room") ),
        Arguments.of( "Title=men's room", ftResponseAdj("title", "men''s room") ),
        Arguments.of( "Title=v1*", "error: Masking op * unsupported for: Title = v1*" ),
        Arguments.of( "Title=v1?", "error: Masking op ? unsupported for: Title = v1?" ),
        Arguments.of( "Title=v1^", "error: Anchor op ^ unsupported for: Title = v1^" ),
        Arguments.of( "Title=\"a\\\"b\"", ftResponseAdj("title", "a\"b") ),
        Arguments.of( "Title=a\\*b", ftResponseAdj("title", "a*b") ),
        Arguments.of( "Title=a\\^b", ftResponseAdj("title", "a^b") ),
        Arguments.of( "Title=a\\?b", ftResponseAdj("title", "a?b") ),
        Arguments.of( "Title=a\\?b", ftResponseAdj("title", "a?b") ),
        Arguments.of( "Title=a\\n", "error: A masking backslash in a CQL string must be followed by"
            + " *, ?, ^, \" or \\ for: Title = a\\n"),
        Arguments.of( "Title=a\\", "error: A CQL string must not end with a masking backslash for: Title = a\\" ),
        Arguments.of( "Title=\"a\\\"\"", ftResponseAdj("title", "a\"") ),
        Arguments.of( "Title=\"a\\\"b\"", ftResponseAdj("title", "a\"b") ),
        Arguments.of( "Title=a\\\\", ftResponseAdj("title", "a\\") ),
        Arguments.of( "Title=a\\\\n", ftResponseAdj("title", "a\\n") ),
        Arguments.of( "Title=a\\\\", ftResponseAdj("title", "a\\") ),
        Arguments.of( "Title=aa\\\\1", ftResponseAdj("title", "aa\\1") ),
        Arguments.of( "Title=ab\\\\\\?", ftResponseAdj("title", "ab\\?") ),
        Arguments.of( "Title=\"b\\\\\"", ftResponseAdj("title", "b\\") ),
        Arguments.of( "Title=\"c\\\\'\"", ftResponseAdj("title", "c\\''") ),
        Arguments.of( "Title=\"c\\\\d\"", ftResponseAdj("title", "c\\d") ),
        Arguments.of( "Title=\"d\\\\\\\\\"", ftResponseAdj("title", "d\\\\") ),
        Arguments.of( "Title=\"x\\\\\\\"\\\\\"", ftResponseAdj("title", "x\\\"\\") ),
        Arguments.of( "Title=\"\"", "title IS NOT NULL" ),
        Arguments.of( "Title<>\"\"", "title <> ''" ),
        Arguments.of( "Title==\"\"", "title = ''" ),
        Arguments.of( "Title==\"*?\"", "title LIKE '%_'" ),
        Arguments.of( "Title==\"\\*\\?\\^\"", "title = '*?^'" ),
        Arguments.of( "Title==\"b\\\\\"", "title = 'b\\'" ),
        Arguments.of( "Title==\"c\\\\'\"", "title = 'c\\'''" ),
        Arguments.of( "Title==\"d\\\\\\\\\"", "title = 'd\\\\'" ),
        Arguments.of( "Title==\"e\\\\\\\"\\\\\"", "title = 'e\\\"\\'" ),
        Arguments.of( "Title>\"\"", "error: Unsupported operator for: Title > \"\"" ),
        Arguments.of( "Title==v1 or title==v2",  "(title = 'v1' OR title = 'v2')"),
        Arguments.of( "isbn=978-3-16-148410-0", "isbn = '978-3-16-148410-0'" ),
        Arguments.of( "isbn=978-3-16-148410-*", "error: Masking op * unsupported for: isbn = 978-3-16-148410-*" ),
        Arguments.of( "cql.allRecords=1 or title==v1", null ),
        Arguments.of( "title==v1 or cql.allRecords=1", null ),
        Arguments.of( "Title==v1 and title==v2", "(title = 'v1' AND title = 'v2')" ),
        Arguments.of( "Title==v1 and cql.allRecords=1", "title = 'v1'" ),
        Arguments.of( "cql.allRecords=1 and Title==v2", "title = 'v2'" ),
        Arguments.of( "Title==v1 not title==v2", "(title = 'v1' AND NOT title = 'v2')" ),
        Arguments.of( "cql.allRecords=1 not title==v2", "NOT (title = 'v2')" ),
        Arguments.of( "title==v1 not cql.allRecords=1", "FALSE" ),
        Arguments.of( "title==v1 prox title==v2", "error: Unsupported operator PROX" ),
        Arguments.of( "cost=1 or cost=2 and cost=3", "((cost=1 OR cost=2) AND cost=3)" ), // boolean are left-assoc and same precedence in CQL
        Arguments.of( "cost=1 or (cost=2 and cost=3)", "(cost=1 OR (cost=2 AND cost=3))" ),
        Arguments.of( "cost=\"\" or cost<>3", "(cost IS NOT NULL OR cost<>3)" ),
        Arguments.of( "cost=1", "cost=1" ),
        Arguments.of( "cost=+1.9", "cost=+1.9" ),
        Arguments.of( "cost=e", "error: Bad numeric for: cost = e" ),
        Arguments.of( "cost=1.5e3", "cost=1.5e3" ),
        Arguments.of( "cost=-1,90", "error: Bad numeric for: cost = -1,90" ),
        Arguments.of( "cost=0x100", "error: Bad numeric for: cost = 0x100" ),
        Arguments.of( "cost==\"\"", "error: Bad numeric for: cost == \"\"" ),
        Arguments.of( "cost>1", "cost>1" ),
        Arguments.of( "cost>=2", "cost>=2" ),
        Arguments.of( "cost==3", "cost=3" ),
        Arguments.of( "cost<>4", "cost<>4" ),
        Arguments.of( "cost<5", "cost<5" ),
        Arguments.of( "cost<=6", "cost<=6" ),
        Arguments.of( "cost adj 7", "error: Unsupported operator for: cost adj 7" ),
        Arguments.of( "cost=\"\"", "cost IS NOT NULL" ),
        Arguments.of( "paid=true", "paid=TRUE" ),
        Arguments.of( "paid=False", "paid=FALSE" ),
        Arguments.of( "paid=fals", "error: Bad boolean for: paid = fals" ),
        Arguments.of( "paid=\"\"", "paid IS NOT NULL" ),
        Arguments.of( "paid==\"\"", "error: Bad boolean for: paid == \"\"" ),
        Arguments.of( "id=null", "error: Invalid UUID for: id = null" ),
        Arguments.of( "id==\"\"", "error: Invalid UUID for: id == \"\"" ),
        Arguments.of( "id=\"\"", "id IS NOT NULL" ),
        Arguments.of( "id=6736bd11-5073-4026-81b5-b70b24179e02", "id='6736bd11-5073-4026-81b5-b70b24179e02'" ),
        Arguments.of( "id=6736BD11-5073-4026-81B5-B70B24179E02", "id='6736bd11-5073-4026-81b5-b70b24179e02'" ),
        Arguments.of( "id<>6736bd11-5073-4026-81b5-b70b24179e02", "id<>'6736bd11-5073-4026-81b5-b70b24179e02'" ),
        Arguments.of( "title==v1 sortby cost", "title = 'v1'"),
        Arguments.of( ">x = \"http://foo.org/p\" title==v1", "title = 'v1'"),
        Arguments.of( "Parrot = dead", ftResponse("parrot", "dead", "phraseto_tsquery", "norwegian") ),
        Arguments.of( "Parrot =\"\"", "error: = \"\" (not null test) is not supported for: Parrot = \"\""),
        Arguments.of( "Parrot == \"x\"", "error: Unsupported operator for: Parrot == x"),
        Arguments.of( "base =\"\"", "error: = \"\" (not null test) is not supported for: base = \"\""),
        Arguments.of( "base == \"x\"", "error: Unsupported operator for: base == x"),
        Arguments.of( "base adj \"x\"", "error: Unsupported operator for: base adj x"),
        Arguments.of( "issn = 3", "issn = '3'"),
        Arguments.of( "issn = ^2?3", "error: Anchor op ^ unsupported for: issn = ^2?3"),
        Arguments.of( "issn = 2?3^", "error: Anchor op ^ unsupported for: issn = 2?3^"),
        Arguments.of( "issn = 2?^3", "error: Anchor op ^ unsupported for: issn = 2?^3"),
        Arguments.of( "issn = 2^3", "error: Anchor op ^ unsupported for: issn = 2^3"),
        Arguments.of( "issn = 2*3", "issn LIKE '2%3'"),
        Arguments.of( "issn = 2'4*", "issn LIKE '2''4%'"),
        Arguments.of( "issn = 2'4", "issn = '2''4'"),
        Arguments.of( "issn = 2%4", "issn = '2%4'"),
        Arguments.of( "issn = 2_4", "issn = '2_4'"),
        Arguments.of( "issn = 2_5*", "issn LIKE '2\\_5%'"),
        Arguments.of( "issn = 2%5*", "issn LIKE '2\\%5%'"),
        Arguments.of( "issn = 2\\", "error: A CQL string must not end with a masking backslash for: issn = 2\\"),
        Arguments.of( "issn = 2\\_%6*", "error: A masking backslash in a CQL string must be followed by"
            + " *, ?, ^, \" or \\ for: issn = 2\\_%6*"),
        Arguments.of( "issn = 2\\?_8\\*", "issn = '2?_8*'"),
        Arguments.of( "issn <> 2*9", "issn NOT LIKE '2%9'"),
        Arguments.of( "issn <> 2_9", "issn <> '2_9'"),
        Arguments.of( "issn == 2_9*", "issn LIKE '2\\_9%'"),
        Arguments.of( ">dc=\"http://dublin.org/\" isbn = 3", "isbn = '3'" )
    );
  }

  @ParameterizedTest
  @MethodSource("cqlQueries")
  void testCqlQueries(String query, String expect) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    pgCqlDefinition.addField("title", new PgCqlFieldText().withFullText().withLikeOps());
    pgCqlDefinition.addField("parrot", new PgCqlFieldText().withFullText("norwegian"));
    pgCqlDefinition.addField("isbn", new PgCqlFieldText().withExact());
    pgCqlDefinition.addField("issn", new PgCqlFieldText().withLikeOps());
    pgCqlDefinition.addField("base", new PgCqlFieldText());
    pgCqlDefinition.addField("cost", new PgCqlFieldNumber());
    pgCqlDefinition.addField("paid", new PgCqlFieldBoolean());
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    try {
      PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query);
      assertThat(pgCqlQuery.getWhereClause(), is(expect));
    } catch (PgCqlException e) {
      assertThat("error: " + e.getMessage(), is(expect));
    }
  }

  static Stream<Arguments> cqlSortQueries() {
    return Stream.of(
        Arguments.of("isbn=1234 sortby foo", "error: Unsupported CQL index: foo", null),
        Arguments.of("paid=1234", null, null),
        Arguments.of("paid=1234 sortby isbn/xx", "error: Unsupported sort modifier: xx", null),
        Arguments.of("paid=1234 sortby isbn", "isbn ASC", "isbn"),
        Arguments.of(">dc=\"http://foo.org/p\" paid=1234 sortby isbn", "isbn ASC", "isbn"),
        Arguments.of("paid=1234 sortby cost/sort.descending title/sort.ascending", "cost DESC, title ASC", "cost, title")
    );
  }

  @ParameterizedTest
  @MethodSource("cqlSortQueries")
  void testSort(String query, String expect, String fields) {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    pgCqlDefinition.addField("title", new PgCqlFieldText().withFullText());
    pgCqlDefinition.addField("isbn", new PgCqlFieldText());
    pgCqlDefinition.addField("cost", new PgCqlFieldNumber());
    pgCqlDefinition.addField("paid", new PgCqlFieldBoolean());
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    try {
      PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query);
      assertThat(pgCqlQuery.getOrderByClause(), is(expect));
      assertThat(pgCqlQuery.getOrderByFields(), is(fields));
    } catch (PgCqlException e) {
      assertThat("error: " + e.getMessage(), is(expect));
    }
  }

  @Test
  void testTimestampField() {
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("datestamp", new PgCqlFieldTimestamp());

    PgCqlQuery pgCqlQuery1 = pgCqlDefinition.parse("datestamp = 2022-02-03T04:05:06");
    assertThat(pgCqlQuery1.getWhereClause(), is("datestamp='2022-02-03T04:05:06'"));

    PgCqlQuery pgCqlQuery2 = pgCqlDefinition.parse("datestamp >= 2022-02-03");
    assertThat(pgCqlQuery2.getWhereClause(), is("datestamp>='2022-02-03'"));


    PgCqlQuery pgCqlQuery3 = pgCqlDefinition.parse("datestamp >= 2022-02-03 and datestamp < 2022-05-03");
    assertThat(pgCqlQuery3.getWhereClause(), is("(datestamp>='2022-02-03' AND datestamp<'2022-05-03')"));

    PgCqlQuery pgCqlQuery4 = pgCqlDefinition.parse("datestamp >= 2022-02-03T04:05:06");
    assertThat(pgCqlQuery4.getWhereClause(), is("datestamp>='2022-02-03T04:05:06'"));

    PgCqlQuery pgCqlQuery5 = pgCqlDefinition.parse("datestamp = 2022-02-03T04:05:06'");
    assertThrows(DateTimeParseException.class, pgCqlQuery5::getWhereClause);
  }
}
