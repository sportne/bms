package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link LengthModeRules}. */
final class LengthModeRulesTest {
  @Test
  void terminatorLiteralReadsTerminatorValueLength() {
    assertEquals(
        "0x00", LengthModeRules.terminatorLiteral(new ResolvedTerminatorValueLength("0x00")));
  }

  @Test
  void terminatorLiteralReadsNestedTerminatorPath() {
    ResolvedTerminatorNode node =
        new ResolvedTerminatorField(
            "outer", new ResolvedTerminatorField("inner", new ResolvedTerminatorMatch("0x7E")));
    assertEquals("0x7E", LengthModeRules.terminatorLiteral(node));
  }

  @Test
  void terminatorLiteralRejectsNonTerminatorLengthMode() {
    assertThrows(
        IllegalStateException.class,
        () -> LengthModeRules.terminatorLiteral(new ResolvedCountFieldLength("count")));
  }
}
