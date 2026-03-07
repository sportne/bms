package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Generates conservative C++ output from the resolved model.
 *
 * <p>For foundation v1, this generator emits structs and method signatures plus placeholder
 * encode/decode method bodies that throw.
 */
public final class CppCodeGenerator {

  /**
   * Writes generated C++ header/source files into the provided output directory.
   *
   * @param schema resolved schema to generate from
   * @param outputDirectory target directory for generated C++ files
   * @throws BmsException if generation fails
   */
  public void generate(ResolvedSchema schema, Path outputDirectory) throws BmsException {
    Map<String, ResolvedMessageType> messageTypeByName = indexMessageTypes(schema);

    for (ResolvedMessageType messageType : schema.messageTypes()) {
      Path namespaceDirectory =
          outputDirectory.resolve(messageType.effectiveNamespace().replace('.', '/'));
      Path headerPath = namespaceDirectory.resolve(messageType.name() + ".hpp");
      Path sourcePath = namespaceDirectory.resolve(messageType.name() + ".cpp");
      ensureSupportedMembers(messageType, sourcePath);

      String headerSource = renderHeader(messageType, messageTypeByName);
      String cppSource = renderSource(messageType);

      try {
        Files.createDirectories(namespaceDirectory);
        Files.writeString(headerPath, headerSource, StandardCharsets.UTF_8);
        Files.writeString(sourcePath, cppSource, StandardCharsets.UTF_8);
      } catch (IOException exception) {
        Diagnostic diagnostic =
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_CPP_IO_ERROR",
                "Failed to write C++ source: " + exception.getMessage(),
                sourcePath.toString(),
                -1,
                -1);
        throw new BmsException("C++ code generation failed.", List.of(diagnostic));
      }
    }
  }

  /**
   * Verifies that this generator supports every member in the message.
   *
   * @param messageType message being generated
   * @param sourcePath output source path used in diagnostics
   * @throws BmsException if unsupported members or type references are found
   */
  private static void ensureSupportedMembers(ResolvedMessageType messageType, Path sourcePath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedField field)) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_CPP_UNSUPPORTED_MEMBER",
                "C++ generator does not support message member type "
                    + member.getClass().getSimpleName()
                    + " in message "
                    + messageType.name()
                    + " yet.",
                sourcePath.toString(),
                -1,
                -1));
        continue;
      }
      if (!(field.typeRef() instanceof PrimitiveTypeRef)
          && !(field.typeRef() instanceof MessageTypeRef)) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_CPP_UNSUPPORTED_TYPE_REF",
                "C++ generator does not support field type reference "
                    + field.typeRef().getClass().getSimpleName()
                    + " for field "
                    + field.name()
                    + " in message "
                    + messageType.name()
                    + " yet.",
                sourcePath.toString(),
                -1,
                -1));
      }
    }

    if (!diagnostics.isEmpty()) {
      throw new BmsException("C++ code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a stable lookup map from message type name to resolved message object.
   *
   * @param schema resolved schema that contains message types
   * @return immutable map keyed by message type name
   */
  private static Map<String, ResolvedMessageType> indexMessageTypes(ResolvedSchema schema) {
    Map<String, ResolvedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ResolvedMessageType messageType : schema.messageTypes()) {
      messageTypeByName.put(messageType.name(), messageType);
    }
    return Map.copyOf(messageTypeByName);
  }

  /**
   * Renders one C++ header file for a resolved message.
   *
   * @param messageType message type to render
   * @param messageTypeByName lookup for resolving cross-message includes
   * @return generated C++ header source
   */
  private static String renderHeader(
      ResolvedMessageType messageType, Map<String, ResolvedMessageType> messageTypeByName) {
    StringBuilder builder = new StringBuilder();
    builder.append("#pragma once\n\n");
    builder.append("#include <cstdint>\n");
    builder.append("#include <span>\n");
    builder.append("#include <vector>\n");

    TreeSet<String> includePaths = new TreeSet<>();
    for (ResolvedField field : messageType.fields()) {
      if (field.typeRef() instanceof MessageTypeRef messageTypeRef) {
        ResolvedMessageType referenced = messageTypeByName.get(messageTypeRef.messageTypeName());
        if (referenced != null) {
          includePaths.add(headerIncludePath(referenced));
        }
      }
    }

    for (String includePath : includePaths) {
      if (!includePath.equals(headerIncludePath(messageType))) {
        builder.append("#include \"").append(includePath).append("\"\n");
      }
    }

    builder.append('\n');
    appendNamespaceOpen(builder, messageType.effectiveNamespace());

    builder.append("struct ").append(messageType.name()).append(" {\n");
    for (ResolvedField field : messageType.fields()) {
      builder
          .append("  ")
          .append(toCppType(field.typeRef(), messageType.effectiveNamespace(), messageTypeByName))
          .append(' ')
          .append(field.name())
          .append("{};\n");
    }

    builder.append("\n");
    builder.append("  std::vector<std::uint8_t> encode() const;\n");
    builder
        .append("  /**\n")
        .append("   * Decodes a message instance from binary input.\n")
        .append("   *\n")
        .append("   * @param data encoded message bytes\n")
        .append("   * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append("   */\n")
        .append("  static ")
        .append(messageType.name())
        .append(" decode(std::span<const std::uint8_t> data);\n");
    builder.append("};\n\n");

    appendNamespaceClose(builder, messageType.effectiveNamespace());
    return builder.toString();
  }

  /**
   * Renders one C++ source file for a resolved message.
   *
   * @param messageType message type to render
   * @return generated C++ source text
   */
  private static String renderSource(ResolvedMessageType messageType) {
    StringBuilder builder = new StringBuilder();
    builder.append("#include \"").append(headerIncludePath(messageType)).append("\"\n\n");
    builder.append("#include <stdexcept>\n\n");

    appendNamespaceOpen(builder, messageType.effectiveNamespace());

    builder
        .append("std::vector<std::uint8_t> ")
        .append(messageType.name())
        .append("::encode() const {\n")
        .append("  throw std::runtime_error(\"Encode is not implemented in foundation v1.\");\n")
        .append("}\n\n");

    builder
        .append("/**\n")
        .append(" * Decodes a message instance from binary input.\n")
        .append(" *\n")
        .append(" * @param data encoded message bytes\n")
        .append(" * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append(" */\n")
        .append(messageType.name())
        .append(' ')
        .append(messageType.name())
        .append("::decode(std::span<const std::uint8_t> data) {\n")
        .append("  (void)data;\n")
        .append("  throw std::runtime_error(\"Decode is not implemented in foundation v1.\");\n")
        .append("}\n\n");

    appendNamespaceClose(builder, messageType.effectiveNamespace());
    return builder.toString();
  }

  /**
   * Resolves a field type reference into a C++ type name.
   *
   * @param typeRef resolved type reference
   * @param currentNamespace namespace of the current generated message
   * @param messageTypeByName lookup for message references
   * @return C++ type name used in generated source
   */
  private static String toCppType(
      ResolvedTypeRef typeRef,
      String currentNamespace,
      Map<String, ResolvedMessageType> messageTypeByName) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().cppTypeName();
    }

    MessageTypeRef messageTypeRef = (MessageTypeRef) typeRef;
    ResolvedMessageType referenced = messageTypeByName.get(messageTypeRef.messageTypeName());
    if (referenced == null) {
      return messageTypeRef.messageTypeName();
    }
    if (referenced.effectiveNamespace().equals(currentNamespace)) {
      return referenced.name();
    }
    return "::" + toCppNamespace(referenced.effectiveNamespace()) + "::" + referenced.name();
  }

  /**
   * Builds the relative include path for one message header.
   *
   * @param messageType message type that owns the header
   * @return include path using slash-separated namespace segments
   */
  private static String headerIncludePath(ResolvedMessageType messageType) {
    return messageType.effectiveNamespace().replace('.', '/') + "/" + messageType.name() + ".hpp";
  }

  /**
   * Appends nested namespace open lines to a source builder.
   *
   * @param builder destination source builder
   * @param namespaceValue dot-delimited namespace
   */
  private static void appendNamespaceOpen(StringBuilder builder, String namespaceValue) {
    for (String segment : splitNamespace(namespaceValue)) {
      builder.append("namespace ").append(segment).append(" {\n");
    }
    builder.append('\n');
  }

  /**
   * Appends nested namespace close lines to a source builder.
   *
   * @param builder destination source builder
   * @param namespaceValue dot-delimited namespace
   */
  private static void appendNamespaceClose(StringBuilder builder, String namespaceValue) {
    List<String> segments = splitNamespace(namespaceValue);
    for (int index = segments.size() - 1; index >= 0; index--) {
      builder.append("}  // namespace ").append(segments.get(index)).append("\n");
    }
  }

  /**
   * Splits a dot-delimited namespace into individual non-blank segments.
   *
   * @param namespaceValue dot-delimited namespace
   * @return namespace segments in order
   */
  private static List<String> splitNamespace(String namespaceValue) {
    String[] rawSegments = namespaceValue.split("\\.");
    List<String> segments = new ArrayList<>();
    for (String segment : rawSegments) {
      if (!segment.isBlank()) {
        segments.add(segment);
      }
    }
    return segments;
  }

  /**
   * Converts a dot-delimited namespace to a C++ {@code ::}-delimited namespace.
   *
   * @param namespaceValue dot-delimited namespace
   * @return C++ namespace string
   */
  private static String toCppNamespace(String namespaceValue) {
    return String.join("::", splitNamespace(namespaceValue));
  }
}
