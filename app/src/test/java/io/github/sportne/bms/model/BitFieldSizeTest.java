package io.github.sportne.bms.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for {@link BitFieldSize}. */
class BitFieldSizeTest {

  /** Contract: xml values map to enum constants and preserve bit width metadata. */
  @Test
  void fromXmlMapsKnownValues() {
    assertEquals(BitFieldSize.U8, BitFieldSize.fromXml("u8"));
    assertEquals(BitFieldSize.U16, BitFieldSize.fromXml("u16"));
    assertEquals(BitFieldSize.U32, BitFieldSize.fromXml("u32"));
    assertEquals(BitFieldSize.U64, BitFieldSize.fromXml("u64"));
    assertEquals(8, BitFieldSize.U8.bitWidth());
    assertEquals("u64", BitFieldSize.U64.xmlValue());
  }

  /** Contract: unknown xml values are rejected with an argument exception. */
  @Test
  void fromXmlRejectsUnknownValue() {
    assertThrows(IllegalArgumentException.class, () -> BitFieldSize.fromXml("u128"));
  }
}
