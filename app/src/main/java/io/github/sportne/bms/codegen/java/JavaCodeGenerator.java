package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedField;
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

  /** Writes generated Java files into the provided output directory. */
  public void generate(ResolvedSchema schema, Path outputDirectory) throws BmsException {
    Map<String, ResolvedMessageType> messageTypeByName = indexMessageTypes(schema);

    for (ResolvedMessageType messageType : schema.messageTypes()) {
      Path packageDirectory =
          outputDirectory.resolve(messageType.effectiveNamespace().replace('.', '/'));
      Path outputPath = packageDirectory.resolve(messageType.name() + ".java");
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

  private static Map<String, ResolvedMessageType> indexMessageTypes(ResolvedSchema schema) {
    Map<String, ResolvedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ResolvedMessageType messageType : schema.messageTypes()) {
      messageTypeByName.put(messageType.name(), messageType);
    }
    return Map.copyOf(messageTypeByName);
  }

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
        .append("  public static ")
        .append(messageType.name())
        .append(" decode(byte[] bytes) {\n")
        .append("    Objects.requireNonNull(bytes, \"bytes\");\n")
        .append("    return decode(ByteBuffer.wrap(bytes));\n")
        .append("  }\n\n");

    builder
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

    builder.append(sharedIoHelpers());
    builder.append("}\n");

    return builder.toString();
  }

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

  private static String byteOrderExpression(Endian endian) {
    if (endian == Endian.LITTLE) {
      return "ByteOrder.LITTLE_ENDIAN";
    }
    return "ByteOrder.BIG_ENDIAN";
  }

  private static String sharedIoHelpers() {
    return """
      private static void writeInt8(ByteArrayOutputStream out, byte value) {
        out.write(value);
      }

      private static void writeUInt8(ByteArrayOutputStream out, short value) {
        out.write(value & 0xFF);
      }

      private static void writeInt16(ByteArrayOutputStream out, short value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Short.BYTES).order(order);
        buffer.putShort(value);
        out.write(buffer.array(), 0, Short.BYTES);
      }

      private static void writeUInt16(ByteArrayOutputStream out, int value, ByteOrder order) {
        writeInt16(out, (short) (value & 0xFFFF), order);
      }

      private static void writeInt32(ByteArrayOutputStream out, int value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES).order(order);
        buffer.putInt(value);
        out.write(buffer.array(), 0, Integer.BYTES);
      }

      private static void writeUInt32(ByteArrayOutputStream out, long value, ByteOrder order) {
        writeInt32(out, (int) (value & 0xFFFFFFFFL), order);
      }

      private static void writeInt64(ByteArrayOutputStream out, long value, ByteOrder order) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES).order(order);
        buffer.putLong(value);
        out.write(buffer.array(), 0, Long.BYTES);
      }

      private static void writeUInt64(ByteArrayOutputStream out, long value, ByteOrder order) {
        writeInt64(out, value, order);
      }

      private static byte readInt8(ByteBuffer input) {
        return input.get();
      }

      private static short readUInt8(ByteBuffer input) {
        return (short) (input.get() & 0xFF);
      }

      private static short readInt16(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        short value = slice.getShort();
        input.position(input.position() + Short.BYTES);
        return value;
      }

      private static int readUInt16(ByteBuffer input, ByteOrder order) {
        return Short.toUnsignedInt(readInt16(input, order));
      }

      private static int readInt32(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        int value = slice.getInt();
        input.position(input.position() + Integer.BYTES);
        return value;
      }

      private static long readUInt32(ByteBuffer input, ByteOrder order) {
        return Integer.toUnsignedLong(readInt32(input, order));
      }

      private static long readInt64(ByteBuffer input, ByteOrder order) {
        ByteBuffer slice = input.slice().order(order);
        long value = slice.getLong();
        input.position(input.position() + Long.BYTES);
        return value;
      }

      private static long readUInt64(ByteBuffer input, ByteOrder order) {
        return readInt64(input, order);
      }
    """;
  }
}
