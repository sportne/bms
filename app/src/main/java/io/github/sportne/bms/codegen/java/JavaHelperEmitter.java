package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Emits helper-oriented Java source sections such as imports and bitfield helpers.
 *
 * <p>This class keeps non-encode/decode emission logic out of {@link JavaCodeGenerator}.
 */
final class JavaHelperEmitter {
  /** Prevents instantiation of this static utility class. */
  private JavaHelperEmitter() {}

  /**
   * Appends import lines required by one generated message class.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void appendImports(
      StringBuilder builder,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext) {
    Set<String> imports = new TreeSet<>();
    imports.add("java.io.ByteArrayOutputStream");
    imports.add("java.nio.ByteBuffer");
    imports.add("java.nio.ByteOrder");
    imports.add("java.nio.charset.StandardCharsets");
    imports.add("java.util.ArrayList");
    imports.add("java.util.Objects");
    if (containsChecksumMember(messageType.members())) {
      imports.add("java.util.zip.CRC32");
    }

    collectMessageImports(imports, messageType, generationContext);

    for (String javaImport : imports) {
      builder.append("import ").append(javaImport).append(";\n");
    }
    builder.append("\n");
  }

  /**
   * Returns whether one member list contains a checksum member recursively.
   *
   * @param members members to inspect
   * @return {@code true} when at least one checksum member is present
   */
  static boolean containsChecksumMember(List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof io.github.sportne.bms.model.resolved.ResolvedChecksum) {
        return true;
      }
      if (member instanceof ResolvedIfBlock resolvedIfBlock
          && containsChecksumMember(resolvedIfBlock.members())) {
        return true;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType
          && containsChecksumMember(resolvedNestedType.members())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Collects message-type imports referenced by this message.
   *
   * @param imports destination import set
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void collectMessageImports(
      Set<String> imports,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext) {
    collectMessageImportsForMembers(
        imports, messageType.members(), messageType.effectiveNamespace(), generationContext);
  }

  /**
   * Collects message-type imports for one member list recursively.
   *
   * @param imports destination import set
   * @param members members to inspect
   * @param currentNamespace namespace of the current generated message
   * @param generationContext reusable lookup maps
   */
  private static void collectMessageImportsForMembers(
      Set<String> imports,
      List<ResolvedMessageMember> members,
      String currentNamespace,
      JavaCodeGenerator.GenerationContext generationContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedField resolvedField) {
        collectMessageImportsFromTypeRef(
            imports, resolvedField.typeRef(), currentNamespace, generationContext);
      }
      if (member instanceof ResolvedArray resolvedArray) {
        collectMessageImportsFromTypeRef(
            imports, resolvedArray.elementTypeRef(), currentNamespace, generationContext);
      }
      if (member instanceof ResolvedVector resolvedVector) {
        collectMessageImportsFromTypeRef(
            imports, resolvedVector.elementTypeRef(), currentNamespace, generationContext);
      }
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        collectMessageImportsForMembers(
            imports, resolvedIfBlock.members(), currentNamespace, generationContext);
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        collectMessageImportsForMembers(
            imports, resolvedNestedType.members(), currentNamespace, generationContext);
      }
    }
  }

  /**
   * Collects message-type imports from one type reference recursively.
   *
   * @param imports destination import set
   * @param typeRef type reference to inspect
   * @param currentNamespace namespace of the current generated message
   * @param generationContext reusable lookup maps
   */
  private static void collectMessageImportsFromTypeRef(
      Set<String> imports,
      ResolvedTypeRef typeRef,
      String currentNamespace,
      JavaCodeGenerator.GenerationContext generationContext) {
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      if (referenced != null
          && !Objects.equals(referenced.effectiveNamespace(), currentNamespace)) {
        imports.add(referenced.effectiveNamespace() + "." + referenced.name());
      }
      return;
    }

    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray != null) {
        collectMessageImportsFromTypeRef(
            imports, resolvedArray.elementTypeRef(), currentNamespace, generationContext);
      }
      return;
    }

    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector != null) {
        collectMessageImportsFromTypeRef(
            imports, resolvedVector.elementTypeRef(), currentNamespace, generationContext);
      }
    }
  }

  /**
   * Appends helper methods for each bitField member.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  static void appendBitFieldHelpers(StringBuilder builder, ResolvedMessageType messageType) {
    appendBitFieldHelpersForMembers(builder, messageType.members());
  }

  /**
   * Appends bitField helper methods for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to scan
   */
  private static void appendBitFieldHelpersForMembers(
      StringBuilder builder, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedBitField resolvedBitField) {
        appendBitFieldRawHelpers(builder, resolvedBitField);
        appendBitFieldFlagHelpers(builder, resolvedBitField);
        appendBitFieldSegmentHelpers(builder, resolvedBitField);
        continue;
      }
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        appendBitFieldHelpersForMembers(builder, resolvedIfBlock.members());
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        appendBitFieldHelpersForMembers(builder, resolvedNestedType.members());
      }
    }
  }

  /**
   * Appends raw getter/setter helpers for one bitField.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField to render
   */
  private static void appendBitFieldRawHelpers(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    String pascalName = toPascalCase(resolvedBitField.name());

    builder
        .append("  private long get")
        .append(pascalName)
        .append("Raw() {\n")
        .append("    return ")
        .append(
            bitFieldRawReadExpression("this." + resolvedBitField.name(), resolvedBitField.size()))
        .append(";\n")
        .append("  }\n\n");

    builder
        .append("  private void set")
        .append(pascalName)
        .append("Raw(long raw) {\n")
        .append("    ")
        .append(
            bitFieldRawWriteStatement(
                "this." + resolvedBitField.name(), "raw", resolvedBitField.size()))
        .append("\n")
        .append("  }\n\n");
  }

  /**
   * Appends flag accessors for one bitField.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField to render
   */
  private static void appendBitFieldFlagHelpers(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    String bitFieldPascalName = toPascalCase(resolvedBitField.name());
    for (ResolvedBitFlag resolvedBitFlag : resolvedBitField.flags()) {
      String flagPascalName = toPascalCase(resolvedBitFlag.name());
      long mask = 1L << resolvedBitFlag.position();

      builder
          .append("  public boolean is")
          .append(bitFieldPascalName)
          .append(flagPascalName)
          .append("() {\n")
          .append("    return (get")
          .append(bitFieldPascalName)
          .append("Raw() & ")
          .append(mask)
          .append("L) != 0L;\n")
          .append("  }\n\n");

      builder
          .append("  public void set")
          .append(bitFieldPascalName)
          .append(flagPascalName)
          .append("(boolean enabled) {\n")
          .append("    long raw = get")
          .append(bitFieldPascalName)
          .append("Raw();\n")
          .append("    if (enabled) {\n")
          .append("      raw |= ")
          .append(mask)
          .append("L;\n")
          .append("    } else {\n")
          .append("      raw &= ~")
          .append(mask)
          .append("L;\n")
          .append("    }\n")
          .append("    set")
          .append(bitFieldPascalName)
          .append("Raw(raw);\n")
          .append("  }\n\n");
    }
  }

  /**
   * Appends segment and variant helpers for one bitField.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField to render
   */
  private static void appendBitFieldSegmentHelpers(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    String bitFieldPascalName = toPascalCase(resolvedBitField.name());

    for (ResolvedBitSegment resolvedBitSegment : resolvedBitField.segments()) {
      String segmentPascalName = toPascalCase(resolvedBitSegment.name());
      int width = resolvedBitSegment.toBit() - resolvedBitSegment.fromBit() + 1;
      long mask = width == 64 ? -1L : (1L << width) - 1L;

      for (ResolvedBitVariant resolvedBitVariant : resolvedBitSegment.variants()) {
        builder
            .append("  public static final long ")
            .append(variantConstantName(resolvedBitField, resolvedBitSegment, resolvedBitVariant))
            .append(" = ")
            .append(resolvedBitVariant.value())
            .append("L;\n");
      }
      if (!resolvedBitSegment.variants().isEmpty()) {
        builder.append("\n");
      }

      builder
          .append("  public long get")
          .append(bitFieldPascalName)
          .append(segmentPascalName)
          .append("() {\n")
          .append("    long raw = get")
          .append(bitFieldPascalName)
          .append("Raw();\n")
          .append("    return (raw >>> ")
          .append(resolvedBitSegment.fromBit())
          .append(") & ")
          .append(mask)
          .append("L;\n")
          .append("  }\n\n");

      builder
          .append("  public void set")
          .append(bitFieldPascalName)
          .append(segmentPascalName)
          .append("(long value) {\n")
          .append("    if (value < 0L || value > ")
          .append(mask)
          .append("L) {\n")
          .append("      throw new IllegalArgumentException(\"Segment ")
          .append(resolvedBitSegment.name())
          .append(" in bitField ")
          .append(resolvedBitField.name())
          .append(" must be in [0, ")
          .append(mask)
          .append("]\");\n")
          .append("    }\n")
          .append("    long raw = get")
          .append(bitFieldPascalName)
          .append("Raw();\n")
          .append("    long clearMask = ~(")
          .append(mask)
          .append("L << ")
          .append(resolvedBitSegment.fromBit())
          .append(");\n")
          .append("    raw = (raw & clearMask) | ((value & ")
          .append(mask)
          .append("L) << ")
          .append(resolvedBitSegment.fromBit())
          .append(");\n")
          .append("    set")
          .append(bitFieldPascalName)
          .append("Raw(raw);\n")
          .append("  }\n\n");
    }
  }

  /**
   * Builds one variant constant name.
   *
   * @param resolvedBitField owning bitField
   * @param resolvedBitSegment owning segment
   * @param resolvedBitVariant variant value
   * @return stable uppercase constant name
   */
  private static String variantConstantName(
      ResolvedBitField resolvedBitField,
      ResolvedBitSegment resolvedBitSegment,
      ResolvedBitVariant resolvedBitVariant) {
    return (resolvedBitField.name()
            + "_"
            + resolvedBitSegment.name()
            + "_"
            + resolvedBitVariant.name())
        .replaceAll("[^A-Za-z0-9]+", "_")
        .toUpperCase(Locale.ROOT);
  }

  /**
   * Converts a member name to PascalCase for generated accessor methods.
   *
   * @param value original identifier
   * @return PascalCase representation
   */
  private static String toPascalCase(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.length() == 0 ? value : builder.toString();
  }

  /**
   * Builds an expression that reads raw bits from one bitField storage variable.
   *
   * @param fieldExpression field expression to read
   * @param bitFieldSize bitField storage size
   * @return Java expression that produces unsigned raw bits as a long
   */
  private static String bitFieldRawReadExpression(
      String fieldExpression, BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> "(" + fieldExpression + " & 0xFFL)";
      case U16 -> "(" + fieldExpression + " & 0xFFFFL)";
      case U32 -> "(" + fieldExpression + " & 0xFFFFFFFFL)";
      case U64 -> fieldExpression;
    };
  }

  /**
   * Builds a statement that writes raw bits back to one bitField storage variable.
   *
   * @param fieldExpression field expression to assign
   * @param rawExpression expression that contains raw bits
   * @param bitFieldSize bitField storage size
   * @return Java assignment statement without trailing newline
   */
  private static String bitFieldRawWriteStatement(
      String fieldExpression, String rawExpression, BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> fieldExpression + " = (short) (" + rawExpression + " & 0xFFL);";
      case U16 -> fieldExpression + " = (int) (" + rawExpression + " & 0xFFFFL);";
      case U32 -> fieldExpression + " = " + rawExpression + " & 0xFFFFFFFFL;";
      case U64 -> fieldExpression + " = " + rawExpression + ";";
    };
  }
}
