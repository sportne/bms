package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link SupportMemberRules}. */
final class SupportMemberRulesTest {
  @Test
  void collectFlattenedMemberNameCollisionsUsesMessageContextLabel() {
    List<ResolvedMessageMember> members = List.of(field("value"), field("value"));
    List<String> collisions = new ArrayList<>();

    SupportMemberRules.collectFlattenedMemberNameCollisions(
        members, new LinkedHashSet<>(), "message", collisions::add);

    assertEquals(List.of("message member name collision for value"), collisions);
  }

  @Test
  void collectFlattenedMemberNameCollisionsUsesNestedContexts() {
    ResolvedIfBlock conditionalBlock =
        new ResolvedIfBlock(
            new ResolvedIfComparison(
                "mode", PrimitiveType.UINT8, IfComparisonOperator.EQ, BigInteger.ONE),
            List.of(field("status"), field("status")));
    ResolvedMessageType nestedType =
        new ResolvedMessageType(
            "Inner", "nested message", "acme.telemetry", List.of(field("code"), field("code")));

    List<String> collisions = new ArrayList<>();
    SupportMemberRules.collectFlattenedMemberNameCollisions(
        List.of(conditionalBlock, nestedType), new LinkedHashSet<>(), "message", collisions::add);

    assertEquals(
        List.of(
            "if block member name collision for status",
            "nested type Inner member name collision for code"),
        collisions);
  }

  @Test
  void isDeclarableMemberMatchesFieldEmissionPolicy() {
    ResolvedArray array =
        new ResolvedArray("values", new PrimitiveTypeRef(PrimitiveType.UINT16), 2, null, "array");
    ResolvedVector vector =
        new ResolvedVector(
            "events",
            new MessageTypeRef("Event"),
            null,
            "vector",
            new ResolvedCountFieldLength("count"));

    assertTrue(SupportMemberRules.isDeclarableMember(field("header")));
    assertTrue(SupportMemberRules.isDeclarableMember(array));
    assertTrue(SupportMemberRules.isDeclarableMember(vector));
    assertFalse(SupportMemberRules.isDeclarableMember(new ResolvedPad(2, "padding")));
  }

  @Test
  void memberNameReturnsDeclarableMemberName() {
    assertEquals("payload", SupportMemberRules.memberName(field("payload")));
    assertEquals(
        "samples",
        SupportMemberRules.memberName(
            new ResolvedArray(
                "samples", new PrimitiveTypeRef(PrimitiveType.UINT8), 4, null, "sample array")));
  }

  /**
   * Builds a primitive field member for tests.
   *
   * @param name field name
   * @return resolved primitive field
   */
  private static ResolvedField field(String name) {
    return new ResolvedField(
        name, new PrimitiveTypeRef(PrimitiveType.UINT8), null, null, null, "test field");
  }
}
