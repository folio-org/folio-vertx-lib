module org.folio.tlib {
  exports org.folio.tlib;
  exports org.folio.tlib.postgres;
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
}
