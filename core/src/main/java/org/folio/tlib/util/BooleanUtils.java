package org.folio.tlib.util;

/**
 * Helper for Boolean objects and boolean primitives.
 */
public class BooleanUtils {
  private BooleanUtils() {
  }

  /**
   * Shortcut for Boolean.TRUE.equals(b).
   *
   * @return true if b is true, false if b is false or null
   */
  public static boolean isTrue(Boolean b) {
    return Boolean.TRUE.equals(b);
  }

  /**
   * Shortcut for Boolean.FALSE.equals(b).
   *
   * @return true if b is false, false if b is true or null
   */
  public static boolean isFalse(Boolean b) {
    return Boolean.FALSE.equals(b);
  }
}
