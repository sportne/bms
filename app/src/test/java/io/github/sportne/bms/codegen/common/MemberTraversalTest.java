package io.github.sportne.bms.codegen.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link MemberTraversal}. */
final class MemberTraversalTest {
  @Test
  void visitDepthFirstPreservesRecursiveMemberOrder() {
    List<String> memberNames = new ArrayList<>();
    MemberTraversal.visitDepthFirst(testMembers(), member -> memberNames.add(memberName(member)));
    assertEquals(List.of("root", "if", "insideIf", "Nested", "insideNested"), memberNames);
  }

  @Test
  void anyMatchSearchesRecursively() {
    assertTrue(
        MemberTraversal.anyMatch(
            testMembers(), member -> "insideNested".equals(memberName(member))));
    assertFalse(
        MemberTraversal.anyMatch(testMembers(), member -> "missing".equals(memberName(member))));
  }

  /**
   * Builds one representative member tree with root, if-block, and nested type nodes.
   *
   * @return representative recursive member list
   */
  private static List<ResolvedMessageMember> testMembers() {
    ResolvedField rootField =
        new ResolvedField(
            "root", new PrimitiveTypeRef(PrimitiveType.UINT8), null, null, null, "root");
    ResolvedField insideIfField =
        new ResolvedField(
            "insideIf", new PrimitiveTypeRef(PrimitiveType.UINT8), null, null, null, "if");
    ResolvedIfBlock ifBlock =
        new ResolvedIfBlock(
            new ResolvedIfComparison(
                "root", PrimitiveType.UINT8, IfComparisonOperator.EQ, BigInteger.ONE),
            List.of(insideIfField));
    ResolvedMessageType nestedType =
        new ResolvedMessageType(
            "Nested",
            "nested",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "insideNested",
                    new PrimitiveTypeRef(PrimitiveType.UINT16),
                    null,
                    null,
                    null,
                    "nested")));
    return List.of(rootField, ifBlock, nestedType);
  }

  /**
   * Returns one stable label for assertions from a member node.
   *
   * @param member member to label
   * @return member label used by test assertions
   */
  private static String memberName(ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      return resolvedField.name();
    }
    if (member instanceof ResolvedIfBlock) {
      return "if";
    }
    return ((ResolvedMessageType) member).name();
  }
}
