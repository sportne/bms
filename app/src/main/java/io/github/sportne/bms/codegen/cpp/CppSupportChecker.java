package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.codegen.common.PrimitiveFieldIndex;
import io.github.sportne.bms.codegen.common.SupportMemberRules;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Orchestrates C++ backend support checks for one resolved message.
 *
 * <p>The detailed member and type-reference checks live in {@link CppMemberSupportChecker} so this
 * class stays focused on high-level flow.
 */
final class CppSupportChecker {
  /** Prevents instantiation of this static utility class. */
  private CppSupportChecker() {}

  /**
   * Verifies that this backend supports every member and type in the message.
   *
   * @param messageType message being generated
   * @param generationContext reusable lookup maps
   * @param sourcePath output source path used in diagnostics
   * @throws BmsException if unsupported members or type references are found
   */
  static void ensureSupportedMembers(
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Path sourcePath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);

    for (ResolvedMessageMember member : messageType.members()) {
      CppMemberSupportChecker.checkMemberSupport(
          member,
          messageType,
          generationContext,
          primitiveFieldByName,
          sourcePath.toString(),
          diagnostics);
    }

    SupportMemberRules.collectFlattenedMemberNameCollisions(
        messageType.members(),
        new TreeSet<>(),
        "message",
        collisionLabel ->
            diagnostics.add(
                CppMemberSupportChecker.unsupportedMemberDiagnostic(
                    messageType.name(), collisionLabel, sourcePath.toString())));

    if (!diagnostics.isEmpty()) {
      throw new BmsException("C++ code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a lookup map from primitive field name to primitive type for one message.
   *
   * @param messageType message being generated
   * @return immutable primitive field lookup map
   */
  private static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    return PrimitiveFieldIndex.collect(messageType);
  }
}
