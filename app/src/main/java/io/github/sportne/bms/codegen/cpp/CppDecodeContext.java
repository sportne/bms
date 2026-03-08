package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.codegen.cpp.CppCodeGenerator.GenerationContext;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import java.util.Map;
import java.util.Objects;

/**
 * Shared state used while emitting C++ decode statements.
 *
 * @param builder destination source builder
 * @param messageType message currently being generated
 * @param generationContext reusable resolved-type lookup maps
 * @param primitiveFieldByName primitive field lookup map for count-field reads
 * @param ownerPrefix owner expression prefix used for count-field references
 */
record CppDecodeContext(
    StringBuilder builder,
    ResolvedMessageType messageType,
    GenerationContext generationContext,
    Map<String, PrimitiveType> primitiveFieldByName,
    String ownerPrefix) {
  /**
   * Creates one C++ decode emission context.
   *
   * @param builder destination source builder
   * @param messageType message currently being generated
   * @param generationContext reusable resolved-type lookup maps
   * @param primitiveFieldByName primitive field lookup map for count-field reads
   * @param ownerPrefix owner expression prefix used for count-field references
   */
  CppDecodeContext(
      StringBuilder builder,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    this.builder = Objects.requireNonNull(builder, "builder");
    this.messageType = Objects.requireNonNull(messageType, "messageType");
    this.generationContext = Objects.requireNonNull(generationContext, "generationContext");
    this.primitiveFieldByName =
        Objects.requireNonNull(primitiveFieldByName, "primitiveFieldByName");
    this.ownerPrefix = Objects.requireNonNull(ownerPrefix, "ownerPrefix");
  }
}
