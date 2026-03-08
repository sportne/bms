package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.codegen.common.PrimitiveFieldIndex;
import io.github.sportne.bms.codegen.common.SupportMemberRules;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates Java backend support checks for one resolved message.
 *
 * <p>The detailed member and type-reference checks live in {@link JavaMemberSupportChecker} so this
 * class stays focused on high-level flow.
 */
final class JavaSupportChecker {
  /** Prevents instantiation of this static utility class. */
  private JavaSupportChecker() {}

  /**
   * Verifies that this backend supports every member and type used by one message.
   *
   * @param messageType message being generated
   * @param generationContext lookup maps shared across generation
   * @param outputPath output file path used in diagnostics
   * @throws BmsException if unsupported members or references are found
   */
  static void ensureSupportedMembers(
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Path outputPath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    Set<String> flattenedMemberNames = new LinkedHashSet<>();

    SupportMemberRules.collectFlattenedMemberNameCollisions(
        messageType.members(),
        flattenedMemberNames,
        "message",
        collisionLabel ->
            diagnostics.add(
                JavaMemberSupportChecker.unsupportedMemberDiagnostic(
                    messageType.name(), collisionLabel, outputPath.toString())));

    for (ResolvedMessageMember member : messageType.members()) {
      JavaMemberSupportChecker.checkMemberSupport(
          member, messageType, generationContext, primitiveFieldByName, outputPath, diagnostics);
    }

    if (!diagnostics.isEmpty()) {
      throw new BmsException(
          "Java code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a map from primitive field name to primitive type for one message.
   *
   * @param messageType resolved message type
   * @return primitive field lookup map
   */
  private static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    return PrimitiveFieldIndex.collect(messageType);
  }
}
