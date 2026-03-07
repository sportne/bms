package io.github.sportne.bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for parsing XML endian literals. */
class EndianTest {

  /** Contract: known XML values map to the expected enum values. */
  @Test
  void fromXmlParsesSupportedValues() {
    assertEquals(Endian.LITTLE, Endian.fromXml("little"));
    assertEquals(Endian.BIG, Endian.fromXml("big"));
  }

  /** Contract: omitted endian attributes are represented as {@code null}. */
  @Test
  void fromXmlReturnsNullForNullInput() {
    assertNull(Endian.fromXml(null));
  }

  /** Contract: unknown strings are rejected instead of silently accepted. */
  @Test
  void fromXmlRejectsUnknownValues() {
    assertThrows(IllegalArgumentException.class, () -> Endian.fromXml("middle"));
  }
}
