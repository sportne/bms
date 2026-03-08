package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Contract tests for {@link ChecksumRangeRules}. */
final class ChecksumRangeRulesTest {
  @Test
  void parseAcceptsValidRange() {
    ChecksumRangeRules.ChecksumRange checksumRange = ChecksumRangeRules.parse("1..9");
    assertEquals(1, checksumRange.startInclusive());
    assertEquals(9, checksumRange.endInclusive());
  }

  @Test
  void parseRejectsInvalidRanges() {
    assertNull(ChecksumRangeRules.parse("9..1"));
    assertNull(ChecksumRangeRules.parse("-1..4"));
    assertNull(ChecksumRangeRules.parse("1...4"));
    assertNull(ChecksumRangeRules.parse("abc"));
  }

  @Test
  void requireThrowsWhenRangeIsInvalid() {
    assertThrows(IllegalStateException.class, () -> ChecksumRangeRules.require("x..y"));
  }
}
