package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates Java source files from the resolved model.
 *
 * <p>Each message type becomes one Java class. The class includes public fields and simple {@code
 * encode}/{@code decode} methods for the currently supported foundation types.
 */
public final class JavaCodeGenerator {
  private static final String SHARED_IO_HELPERS =
      """
      /**
       * Writes one signed 8-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       */
      private static void writeInt8(ByteArrayOutputStream out, byte value) {
        out.write(value);
      }

      /**
       * Writes one unsigned 8-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       */
      private static void writeUInt8(ByteArrayOutputStream out, short value) {
        out.write(value & 0xFF);
      }

      /**
       * Writes one signed 16-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeInt16(ByteArrayOutputStream out, short value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES).order(order);
        buffer.putShort(value);
        out.write(buffer.array(), 0, Short.BYTES);
      }

      /**
       * Writes one unsigned 16-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeUInt16(ByteArrayOutputStream out, int value, ByteOrder order) {
        writeInt16(out, (short) (value & 0xFFFF), order);
      }

      /**
       * Writes one signed 32-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeInt32(ByteArrayOutputStream out, int value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(order);
        buffer.putInt(value);
        out.write(buffer.array(), 0, Integer.BYTES);
      }

      /**
       * Writes one unsigned 32-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeUInt32(ByteArrayOutputStream out, long value, ByteOrder order) {
        writeInt32(out, (int) (value & 0xFFFFFFFFL), order);
      }

      /**
       * Writes one signed 64-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeInt64(ByteArrayOutputStream out, long value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(order);
        buffer.putLong(value);
        out.write(buffer.array(), 0, Long.BYTES);
      }

      /**
       * Writes one unsigned 64-bit value.
       *
       * @param out destination byte stream
       * @param value value to write
       * @param order byte order to use
       */
      private static void writeUInt64(ByteArrayOutputStream out, long value, ByteOrder order) {
        writeInt64(out, value, order);
      }

      /**
       * Reads one signed 8-bit value.
       *
       * @param input source byte buffer
       * @return decoded value
       */
      private static byte readInt8(ByteBuffer input) {
        return input.get();
      }

      /**
       * Reads one unsigned 8-bit value.
       *
       * @param input source byte buffer
       * @return decoded value
       */
      private static short readUInt8(ByteBuffer input) {
        return (short) (input.get() & 0xFF);
      }

      /**
       * Reads one signed 16-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static short readInt16(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        short value = slice.getShort();
        input.position(input.position() + Short.BYTES);
        return value;
      }

      /**
       * Reads one unsigned 16-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static int readUInt16(ByteBuffer input, ByteOrder order) {
        return Short.toUnsignedInt(readInt16(input, order));
      }

      /**
       * Reads one signed 32-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static int readInt32(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        int value = slice.getInt();
        input.position(input.position() + Integer.BYTES);
        return value;
      }

      /**
       * Reads one unsigned 32-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static long readUInt32(ByteBuffer input, ByteOrder order) {
        return Integer.toUnsignedLong(readInt32(input, order));
      }

      /**
       * Reads one signed 64-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static long readInt64(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        long value = slice.getLong();
        input.position(input.position() + Long.BYTES);
        return value;
      }

      /**
       * Reads one unsigned 64-bit value.
       *
       * @param input source byte buffer
       * @param order byte order to use
       * @return decoded value
       */
      private static long readUInt64(ByteBuffer input, ByteOrder order) {
        return readInt64(input, order);
      }
      """
          .indent(2)
          .replace("  \n", "\n");

  /**
   * Writes generated Java files into the provided output directory.
   *
   * @param schema resolved schema to generate from
   * @param outputDirectory target directory for generated Java files
   * @throws BmsException if generation fails
   */
  public void generate(ResolvedSchema schema, Path outputDirectory) throws BmsException {
    Map<String, ResolvedMessageType> messageTypeByName = indexMessageTypes(schema);

    for (ResolvedMessageType messageType : schema.messageTypes()) {
      Path packageDirectory =
          outputDirectory.resolve(messageType.effectiveNamespace().replace('.', '/'));
      Path outputPath = packageDirectory.resolve(messageType.name() + ".java");
      ensureSupportedMembers(messageType, outputPath);
      String source = renderMessageType(messageType, messageTypeByName);
      try {
        Files.createDirectories(packageDirectory);
        Files.writeString(outputPath, source, StandardCharsets.UTF_8);
      } catch (IOException exception) {
        Diagnostic diagnostic =
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_JAVA_IO_ERROR",
                "Failed to write Java source: " + exception.getMessage(),
                outputPath.toString(),
                -1,
                -1);
        throw new BmsException("Java code generation failed.", List.of(diagnostic));
      }
    }
  }

  /**
   * Verifies that this generator supports every member in the message.
   *
   * @param messageType message being generated
   * @param outputPath output file path used in diagnostics
   * @throws BmsException if unsupported members or type references are found
   */
  private static void ensureSupportedMembers(ResolvedMessageType messageType, Path outputPath)
      throws BmsException {
    List<Diagnostic> diagnostics = new java.util.ArrayList<>();
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedField field)) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_JAVA_UNSUPPORTED_MEMBER",
                "Java generator does not support message member type "
                    + member.getClass().getSimpleName()
                    + " in message "
                    + messageType.name()
                    + " yet.",
                outputPath.toString(),
                -1,
                -1));
        continue;
      }

      if (!(field.typeRef() instanceof PrimitiveTypeRef)
          && !(field.typeRef() instanceof MessageTypeRef)) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "GENERATOR_JAVA_UNSUPPORTED_TYPE_REF",
                "Java generator does not support field type reference "
                    + field.typeRef().getClass().getSimpleName()
                    + " for field "
                    + field.name()
                    + " in message "
                    + messageType.name()
                    + " yet.",
                outputPath.toString(),
                -1,
                -1));
      }
    }

    if (!diagnostics.isEmpty()) {
      throw new BmsException(
          "Java code generation failed due to unsupported members.", diagnostics);
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
   * Renders one complete Java class source file for a resolved message type.
   *
   * @param messageType message type to render
   * @param messageTypeByName lookup for resolving cross-message references
   * @return generated Java source text
   */
  private static String renderMessageType(
      ResolvedMessageType messageType, Map<String, ResolvedMessageType> messageTypeByName) {
    StringBuilder builder = new StringBuilder();

    builder.append("package ").append(messageType.effectiveNamespace()).append(";\n\n");

    Set<String> imports = new TreeSet<>();
    imports.add("java.io.ByteArrayOutputStream");
    imports.add("java.nio.ByteBuffer");
    imports.add("java.nio.ByteOrder");
    imports.add("java.util.Objects");

    for (ResolvedField field : messageType.fields()) {
      if (field.typeRef() instanceof MessageTypeRef messageTypeRef) {
        ResolvedMessageType referenced = messageTypeByName.get(messageTypeRef.messageTypeName());
        if (referenced != null
            && !Objects.equals(referenced.effectiveNamespace(), messageType.effectiveNamespace())) {
          imports.add(referenced.effectiveNamespace() + "." + referenced.name());
        }
      }
    }

    for (String javaImport : imports) {
      builder.append("import ").append(javaImport).append(";\n");
    }
    builder.append("\n");

    builder.append("public final class ").append(messageType.name()).append(" {\n");

    for (ResolvedField field : messageType.fields()) {
      builder
          .append("  public ")
          .append(toJavaType(field.typeRef(), messageTypeByName))
          .append(' ')
          .append(field.name())
          .append(";\n");
    }

    builder.append("\n");
    builder.append("  public byte[] encode() {\n");
    builder.append("    ByteArrayOutputStream out = new ByteArrayOutputStream();\n");
    for (ResolvedField field : messageType.fields()) {
      appendEncodeLine(builder, field, messageTypeByName);
    }
    builder.append("    return out.toByteArray();\n");
    builder.append("  }\n\n");

    builder
        .append("  /**\n")
        .append("   * Decodes a message instance from a byte array.\n")
        .append("   *\n")
        .append("   * @param bytes encoded message bytes\n")
        .append("   * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append("   */\n")
        .append("  public static ")
        .append(messageType.name())
        .append(" decode(byte[] bytes) {\n")
        .append("    Objects.requireNonNull(bytes, \"bytes\");\n")
        .append("    return decode(ByteBuffer.wrap(bytes));\n")
        .append("  }\n\n");

    builder
        .append("  /**\n")
        .append("   * Decodes a message instance from a byte buffer.\n")
        .append("   *\n")
        .append("   * @param input buffer positioned at the start of this message\n")
        .append("   * @return decoded ")
        .append(messageType.name())
        .append(" value\n")
        .append("   */\n")
        .append("  public static ")
        .append(messageType.name())
        .append(" decode(ByteBuffer input) {\n")
        .append("    Objects.requireNonNull(input, \"input\");\n")
        .append("    ")
        .append(messageType.name())
        .append(" value = new ")
        .append(messageType.name())
        .append("();\n");

    for (ResolvedField field : messageType.fields()) {
      appendDecodeLine(builder, field, messageTypeByName);
    }

    builder.append("    return value;\n");
    builder.append("  }\n\n");

    builder.append(SHARED_IO_HELPERS);
    builder.append("}\n");

    return builder.toString();
  }

  /**
   * Resolves a field type reference into a Java type name.
   *
   * @param typeRef resolved type reference
   * @param messageTypeByName lookup for message references
   * @return Java type name used in generated source
   */
  private static String toJavaType(
      ResolvedTypeRef typeRef, Map<String, ResolvedMessageType> messageTypeByName) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().javaTypeName();
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType messageType = messageTypeByName.get(messageTypeRef.messageTypeName());
      return messageType == null ? messageTypeRef.messageTypeName() : messageType.name();
    }
    throw new IllegalStateException("Unhandled type reference: " + typeRef);
  }

  /**
   * Appends Java encode statements for one field.
   *
   * @param builder destination source builder
   * @param field resolved field to encode
   * @param messageTypeByName lookup for message references
   */
  private static void appendEncodeLine(
      StringBuilder builder,
      ResolvedField field,
      Map<String, ResolvedMessageType> messageTypeByName) {
    if (field.typeRef() instanceof PrimitiveTypeRef primitiveTypeRef) {
      PrimitiveType primitiveType = primitiveTypeRef.primitiveType();
      String byteOrder = byteOrderExpression(field.endian());
      switch (primitiveType) {
        case UINT8 -> builder
            .append("    writeUInt8(out, this.")
            .append(field.name())
            .append(");\n");
        case UINT16 -> builder
            .append("    writeUInt16(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
        case UINT32 -> builder
            .append("    writeUInt32(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
        case UINT64 -> builder
            .append("    writeUInt64(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
        case INT8 -> builder.append("    writeInt8(out, this.").append(field.name()).append(");\n");
        case INT16 -> builder
            .append("    writeInt16(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
        case INT32 -> builder
            .append("    writeInt32(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
        case INT64 -> builder
            .append("    writeInt64(out, this.")
            .append(field.name())
            .append(", ")
            .append(byteOrder)
            .append(");\n");
      }
      return;
    }

    MessageTypeRef messageTypeRef = (MessageTypeRef) field.typeRef();
    String javaType = toJavaType(messageTypeRef, messageTypeByName);
    builder
        .append("    Objects.requireNonNull(this.")
        .append(field.name())
        .append(", \"")
        .append(field.name())
        .append("\");\n")
        .append("    byte[] encoded")
        .append(javaType)
        .append(" = this.")
        .append(field.name())
        .append(".encode();\n")
        .append("    out.write(encoded")
        .append(javaType)
        .append(", 0, encoded")
        .append(javaType)
        .append(".length);\n");
  }

  /**
   * Appends Java decode statements for one field.
   *
   * @param builder destination source builder
   * @param field resolved field to decode
   * @param messageTypeByName lookup for message references
   */
  private static void appendDecodeLine(
      StringBuilder builder,
      ResolvedField field,
      Map<String, ResolvedMessageType> messageTypeByName) {
    if (field.typeRef() instanceof PrimitiveTypeRef primitiveTypeRef) {
      PrimitiveType primitiveType = primitiveTypeRef.primitiveType();
      String byteOrder = byteOrderExpression(field.endian());
      switch (primitiveType) {
        case UINT8 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readUInt8(input);\n");
        case UINT16 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readUInt16(input, ")
            .append(byteOrder)
            .append(");\n");
        case UINT32 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readUInt32(input, ")
            .append(byteOrder)
            .append(");\n");
        case UINT64 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readUInt64(input, ")
            .append(byteOrder)
            .append(");\n");
        case INT8 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readInt8(input);\n");
        case INT16 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readInt16(input, ")
            .append(byteOrder)
            .append(");\n");
        case INT32 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readInt32(input, ")
            .append(byteOrder)
            .append(");\n");
        case INT64 -> builder
            .append("    value.")
            .append(field.name())
            .append(" = readInt64(input, ")
            .append(byteOrder)
            .append(");\n");
      }
      return;
    }

    MessageTypeRef messageTypeRef = (MessageTypeRef) field.typeRef();
    String javaType = toJavaType(messageTypeRef, messageTypeByName);
    builder
        .append("    value.")
        .append(field.name())
        .append(" = ")
        .append(javaType)
        .append(".decode(input);\n");
  }

  /**
   * Converts a BMS endian value to the Java {@link ByteOrder} constant expression.
   *
   * @param endian resolved endian value
   * @return Java source expression for the matching byte order
   */
  private static String byteOrderExpression(Endian endian) {
    if (endian == Endian.LITTLE) {
      return "ByteOrder.LITTLE_ENDIAN";
    }
    return "ByteOrder.BIG_ENDIAN";
  }
}
