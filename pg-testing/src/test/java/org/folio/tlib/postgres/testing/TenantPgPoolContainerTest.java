package org.folio.tlib.postgres.testing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TenantPgPoolContainerTest {

  @ParameterizedTest
  @MethodSource
  void postgresImage(Map<String, String> env, String expected) {
    assertThat(TenantPgPoolContainer.postgresImage(env), is(expected));
  }

  static Stream<Arguments> postgresImage() {
    return Stream.of(
        Arguments.of(Map.of(), "postgres:12-alpine"),
        Arguments.of(Map.of("foo", "bar"), "postgres:12-alpine"),
        Arguments.of(Map.of("foo", "bar", "TESTCONTAINERS_POSTGRES_IMAGE", "x:y-z"), "x:y-z")
        );
  }
}
