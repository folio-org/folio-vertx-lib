package org.folio.tlib.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BooleanUtilsTest {

  @ParameterizedTest
  @CsvSource(textBlock = """
      true, true
      false, false
      , false
      """)
  void isTrue(Boolean b, boolean expected) {
    assertThat(BooleanUtils.isTrue(b), is(expected));
  }

  @ParameterizedTest
  @CsvSource(textBlock = """
      false, true
      true, false
      , false
      """)
  void isFalse(Boolean b, boolean expected) {
    assertThat(BooleanUtils.isFalse(b), is(expected));
  }

}
