## 3.1.0 WIP

* [VERTXLIB-43](https://issues.folio.org/browse/VERTXLIB-43) Add timestamp field class for date-only and date-time fields w/o offset.

## 3.0.0 2023-01-27

Changes to the PgCql API. Introduce `PgCqlDefinition` interface with
functionality previously part of `PgCqlQuery`.
Example of old and new code:

    // Version 2
    PgCqlQuery pgCqlQuery = PgCqlQuery.query();
    pgCqlQuery.addField(new PgCqlField("cql.allRecords", PgCqlField.Type.ALWAYS_MATCHES));
    pgCqlQuery.addField(new PgCqlField("id", PgCqlField.Type.UUID));
    pgCqlQuery.addField(new PgCqlField("key", PgCqlField.Type.TEXT));
    pgCqlQuery.addField(new PgCqlField("title", PgCqlField.Type.FULLTEXT));
    pgCqlQuery.parse(query);

    // Version 3
    PgCqlDefinition pgCqlDefinition = PgCqlDefinition.create();
    pgCqlDefinition.addField("cql.allRecords", new PgCqlFieldAlwaysMatches());
    pgCqlDefinition.addField("id", new PgCqlFieldUuid());
    pgCqlDefinition.addField("key", new PgCqlFieldText().withExact());
    pgCqlDefinition.addField("title", new PgCqlFieldText().withFullText());
    PgCqlQuery pgCqlQuery = pgCqlDefinition.parse(query);

 * [VERTXLIB-39](https://issues.folio.org/browse/VERTXLIB-39) Require the X-Okapi-Tenant header in the OpenAPI spec.
 * [VERTXLIB-38](https://issues.folio.org/browse/VERTXLIB-38) CQL masking (like SQL OP), phrase search for full text.
 * [VERTXLIB-35](https://issues.folio.org/browse/VERTXLIB-35) OpenAPI: OOM for any large POST/PUT
 * [VERTXLIB-33](https://issues.folio.org/browse/VERTXLIB-33) Extensible CQL fields
 * [VERTXLIB-31](https://issues.folio.org/browse/VERTXLIB-31) Split PgCqlPquery into query and definition

## 2.0.0 2022-06-14

 * [VERTXLIB-22](https://issues.folio.org/browse/VERTXLIB-22) Remove schema substitution in
TenantPgPool.{query, preparedQuery,execute}. In version 1 '{schema}' in queries was substitued with schema (for Tenant).
 * [VERTXLIB-21](https://issues.folio.org/browse/VERTXLIB-21) Upgrade to Vert.x 4.3.1, okapi 4.14.1

## 1.1.0 2022-05-06

New features:

 * [VERTXLIB-18](https://issues.folio.org/browse/VERTXLIB-18) Enable SCRAM-SHA-256 PostgreSQL passwords

Fixes:

 * [VERTXLIB-17](https://issues.folio.org/browse/VERTXLIB-17) Upgrade Vert.x to 4.2.7, Log4j 2.17.2 Okapi 4.13.2

## 1.0.1 2022-03-04

 * [VERTXLIB-14](https://issues.folio.org/browse/VERTXLIB-14) Allow _ (underscore) as part of tenant id
 * [VERTXLIB-13](https://issues.folio.org/browse/VERTXLIB-13) Fix Sporadic unit test fail
 * [VERTXLIB-12](https://issues.folio.org/browse/VERTXLIB-12) Update to Vert.x 4.2.5, Okapi 4.13.0
 * [VERTXLIB-11](https://issues.folio.org/browse/VERTXLIB-11) Deploy *-source.jar and *-javadoc.jar to folio-nexus

## 1.0.0 2022-01-28

Initial release
