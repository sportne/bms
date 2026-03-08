package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds lookup maps for primitive scalar fields in one resolved message tree.
 *
 * <p>The first occurrence of each field name is kept so behavior remains deterministic.
 */
public final class PrimitiveFieldIndex {
  /** Creates a utility-only helper class. */
  private PrimitiveFieldIndex() {}

  /**
   * Collects primitive scalar fields from one message recursively.
   *
   * @param messageType message definition whose members should be scanned
   * @return immutable map from field name to primitive type
   */
  public static Map<String, PrimitiveType> collect(ResolvedMessageType messageType) {
    Map<String, PrimitiveType> primitiveFieldByName = new LinkedHashMap<>();
    MemberTraversal.visitDepthFirst(
        messageType.members(), member -> collectMember(member, primitiveFieldByName));
    return Map.copyOf(primitiveFieldByName);
  }

  /**
   * Adds one member to the primitive-field map when it is a primitive scalar field.
   *
   * @param member member to inspect
   * @param primitiveFieldByName destination primitive-field map
   */
  private static void collectMember(
      ResolvedMessageMember member, Map<String, PrimitiveType> primitiveFieldByName) {
    if (member instanceof ResolvedField resolvedField
        && resolvedField.typeRef() instanceof PrimitiveTypeRef primitiveTypeRef) {
      primitiveFieldByName.putIfAbsent(resolvedField.name(), primitiveTypeRef.primitiveType());
    }
  }
}
