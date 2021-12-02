module org.folio.tlib.pg.testing {
  exports org.folio.tlib.pg.testing;
  requires org.folio.tlib;
  requires cql.java;
  requires io.vertx.client.sql;
  requires io.vertx.client.sql.pg;
  requires io.vertx.core;
  requires io.vertx.web.client;
  requires io.vertx.web;
  requires io.vertx.web.openapi;
  requires io.vertx.web.validation;
  requires java.validation;
  requires okapi.common;
  requires org.apache.logging.log4j;
  requires transitive testcontainers;
}
