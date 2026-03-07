package io.github.sportne.bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for {@link FloatEncoding}. */
class FloatEncodingTest {

  /** Contract: xml values map to enum constants and can be rendered back to xml values. */
  @Test
  void fromXmlMapsKnownValues() {
    assertEquals(FloatEncoding.IEEE754, FloatEncoding.fromXml("ieee754"));
    assertEquals(FloatEncoding.SCALED, FloatEncoding.fromXml("scaled"));
    assertEquals("ieee754", FloatEncoding.IEEE754.xmlValue());
    assertEquals("scaled", FloatEncoding.SCALED.xmlValue());
  }

  /** Contract: unknown xml values are rejected with an argument exception. */
  @Test
  void fromXmlRejectsUnknownValue() {
    assertThrows(IllegalArgumentException.class, () -> FloatEncoding.fromXml("custom"));
  }
}
