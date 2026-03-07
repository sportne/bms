package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
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

  /** Writes generated C++ header/source files into the provided output directory. */
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
      if (field.typeRef() instanceof FloatTypeRef || field.typeRef() instanceof ScaledIntTypeRef) {
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

  private static Map<String, ResolvedMessageType> indexMessageTypes(ResolvedSchema schema) {
    Map<String, ResolvedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ResolvedMessageType messageType : schema.messageTypes()) {
      messageTypeByName.put(messageType.name(), messageType);
    }
    return Map.copyOf(messageTypeByName);
  }

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
        .append("  static ")
        .append(messageType.name())
        .append(" decode(std::span<const std::uint8_t> data);\n");
    builder.append("};\n\n");

    appendNamespaceClose(builder, messageType.effectiveNamespace());
    return builder.toString();
  }

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

  private static String headerIncludePath(ResolvedMessageType messageType) {
    return messageType.effectiveNamespace().replace('.', '/') + "/" + messageType.name() + ".hpp";
  }

  private static void appendNamespaceOpen(StringBuilder builder, String namespaceValue) {
    for (String segment : splitNamespace(namespaceValue)) {
      builder.append("namespace ").append(segment).append(" {\n");
    }
    builder.append('\n');
  }

  private static void appendNamespaceClose(StringBuilder builder, String namespaceValue) {
    List<String> segments = splitNamespace(namespaceValue);
    for (int index = segments.size() - 1; index >= 0; index--) {
      builder.append("}  // namespace ").append(segments.get(index)).append("\n");
    }
  }

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

  private static String toCppNamespace(String namespaceValue) {
    return String.join("::", splitNamespace(namespaceValue));
  }
}
