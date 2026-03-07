package io.github.sportne.bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EndianTest {

  @Test
  void fromXmlParsesSupportedValues() {
    assertEquals(Endian.LITTLE, Endian.fromXml("little"));
    assertEquals(Endian.BIG, Endian.fromXml("big"));
  }

  @Test
  void fromXmlReturnsNullForNullInput() {
    assertNull(Endian.fromXml(null));
  }

  @Test
  void fromXmlRejectsUnknownValues() {
    assertThrows(IllegalArgumentException.class, () -> Endian.fromXml("middle"));
  }
}
