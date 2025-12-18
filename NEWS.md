## 3.4.1 2025-12-18

* [VERTXLIB-77](https://folio-org.atlassian.net/browse/VERTXLIB-77) Vert.x 4.5.23 fixing CVE-2025-67735 Netty CRLF injection request smuggling

## 3.4.0 2025-03-11

Release for Sunflower.

* [VERTXLIB-58](https://folio-org.atlassian.net/browse/VERTXLIB-58) Migrate Java from 17 to 21
* [VERTXLIB-59](https://folio-org.atlassian.net/browse/VERTXLIB-59) Upgrade dependencies for Sunflower: vertx, okapi-common, etc.
* [VERTXLIB-60](https://folio-org.atlassian.net/browse/VERTXLIB-60) PostgreSQLContainer withStartupAttempts(3)

## 3.3.0 2024-10-30

Release for Ramsons.

* [VERTXLIB-56](https://folio-org.atlassian.net/browse/VERTXLIB-56) Upgrade dependencies for Ramsons: Vert.x 4.5.10, Postgres 16, etc.
* [VERTXLIB-57](https://folio-org.atlassian.net/browse/VERTXLIB-57) Support DB\_RECONNECTATTEMPTS, DB\_RECONNECTINTERVAL env vars

## 3.2.0 2024-02-24

Release for Quesnelia with dependency upgrades:

* [VERTXLIB-52](https://folio-org.atlassian.net/browse/VERTXLIB-52) Implement TESTCONTAINERS\_POSTGRES\_IMAGE
* [VERTXLIB-54](https://folio-org.atlassian.net/browse/VERTXLIB-54) Upgrade dependencies for Quesnelia: Vert.x 4.5.3, log4j 2.23.0, commons-compress 1.26.0, etc.

## 3.1.3 2023-11-08

Bug fix:

* [VERTXLIB-50](https://issues.folio.org/browse/VERTXLIB-50) Vert.x 4.4.6, Testcontainers 1.19.1

## 3.1.2 2023-10-13

Bug fix:

* [VERTXLIB-49](https://issues.folio.org/browse/VERTXLIB-49) Disable dependency reduced pom, disable mod-example javadoc

## 3.1.1 2023-10-06

Release for Poppy with dependency upgrades:

* [VERTXLIB-44](https://issues.folio.org/browse/VERTXLIB-44), [VERTXLIB-46](https://issues.folio.org/browse/VERTXLIB-46) Poppy dependecy upgrades: Vert.x 4.4.5, ...
* [VERTXLIB-45](https://issues.folio.org/browse/VERTXLIB-45) Update to Java 17
* [VERTXLIB-47](https://issues.folio.org/browse/VERTXLIB-47) Fix java 17 javadoc errors

## 3.1.0 2023-04-26

* [VERTXLIB-43](https://issues.folio.org/browse/VERTXLIB-43) Add timestamp field class for date-only and date-time fields w/o offset.
* [VERTXLIB-37](https://issues.folio.org/browse/VERTXLIB-37) Structured logging using FolioLoggingContext.

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
