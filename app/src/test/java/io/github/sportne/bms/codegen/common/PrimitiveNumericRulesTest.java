package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link PrimitiveNumericRules}. */
final class PrimitiveNumericRulesTest {
  @Test
  void parseNumericLiteralSupportsDecimalAndHexForms() {
    assertEquals(BigInteger.valueOf(42), PrimitiveNumericRules.parseNumericLiteral("42"));
    assertEquals(BigInteger.valueOf(42), PrimitiveNumericRules.parseNumericLiteral("0x2A"));
    assertEquals(BigInteger.valueOf(-42), PrimitiveNumericRules.parseNumericLiteral("-0x2A"));
  }

  @Test
  void fitsPrimitiveRangeValidatesSignedAndUnsignedRanges() {
    assertTrue(
        PrimitiveNumericRules.fitsPrimitiveRange(BigInteger.valueOf(255), PrimitiveType.UINT8));
    assertFalse(
        PrimitiveNumericRules.fitsPrimitiveRange(BigInteger.valueOf(256), PrimitiveType.UINT8));
    assertTrue(
        PrimitiveNumericRules.fitsPrimitiveRange(BigInteger.valueOf(-128), PrimitiveType.INT8));
    assertFalse(
        PrimitiveNumericRules.fitsPrimitiveRange(BigInteger.valueOf(-129), PrimitiveType.INT8));
  }

  @Test
  void isUnsignedPrimitiveIdentifiesUnsignedTypes() {
    assertTrue(PrimitiveNumericRules.isUnsignedPrimitive(PrimitiveType.UINT16));
    assertFalse(PrimitiveNumericRules.isUnsignedPrimitive(PrimitiveType.INT16));
  }
}
