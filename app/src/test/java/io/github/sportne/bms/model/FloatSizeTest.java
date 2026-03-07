package io.github.sportne.bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for {@link FloatSize}. */
class FloatSizeTest {

  /** Contract: xml values map to enum constants and can be rendered back to xml values. */
  @Test
  void fromXmlMapsKnownValues() {
    assertEquals(FloatSize.F16, FloatSize.fromXml("f16"));
    assertEquals(FloatSize.F32, FloatSize.fromXml("f32"));
    assertEquals(FloatSize.F64, FloatSize.fromXml("f64"));
    assertEquals("f16", FloatSize.F16.xmlValue());
    assertEquals("f64", FloatSize.F64.xmlValue());
  }

  /** Contract: unknown xml values are rejected with an argument exception. */
  @Test
  void fromXmlRejectsUnknownValue() {
    assertThrows(IllegalArgumentException.class, () -> FloatSize.fromXml("f128"));
  }
}
