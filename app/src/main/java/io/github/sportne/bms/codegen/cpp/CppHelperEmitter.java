package io.github.sportne.bms.codegen.cpp;

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
import java.math.BigInteger;
import java.util.Locale;
import java.util.Set;

/**
 * Emits helper-oriented C++ source sections such as includes and bitfield helper methods.
 *
 * <p>This class keeps non-encode/decode emission logic out of {@link CppCodeGenerator}.
 */
final class CppHelperEmitter {
  /** Prevents instantiation of this static utility class. */
  private CppHelperEmitter() {}

  /**
   * Collects cross-message include paths needed for one message.
   *
   * @param includePaths destination set of include paths
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  static void collectMessageIncludes(
      Set<String> includePaths,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext) {
    for (ResolvedMessageMember member : messageType.members()) {
      collectMessageIncludesForMember(
          member, messageType.effectiveNamespace(), generationContext, includePaths);
    }
  }

  /**
   * Collects cross-message includes needed for one resolved member.
   *
   * @param member member to inspect
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param includePaths destination set of include paths
   */
  private static void collectMessageIncludesForMember(
      ResolvedMessageMember member,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext,
      Set<String> includePaths) {
    if (member instanceof ResolvedField resolvedField) {
      collectMessageIncludesForTypeRef(
          resolvedField.typeRef(), currentNamespace, generationContext, includePaths);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      collectMessageIncludesForTypeRef(
          resolvedArray.elementTypeRef(), currentNamespace, generationContext, includePaths);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      collectMessageIncludesForTypeRef(
          resolvedVector.elementTypeRef(), currentNamespace, generationContext, includePaths);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      for (ResolvedMessageMember nestedMember : resolvedIfBlock.members()) {
        collectMessageIncludesForMember(
            nestedMember, currentNamespace, generationContext, includePaths);
      }
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      for (ResolvedMessageMember nestedMember : resolvedNestedType.members()) {
        collectMessageIncludesForMember(
            nestedMember, currentNamespace, generationContext, includePaths);
      }
    }
  }

  /**
   * Collects cross-message includes needed for one resolved type reference.
   *
   * @param typeRef type reference to inspect
   * @param currentNamespace namespace of the generated message
   * @param generationContext reusable lookup maps
   * @param includePaths destination set of include paths
   */
  private static void collectMessageIncludesForTypeRef(
      ResolvedTypeRef typeRef,
      String currentNamespace,
      CppCodeGenerator.GenerationContext generationContext,
      Set<String> includePaths) {
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      if (referenced != null && !referenced.effectiveNamespace().equals(currentNamespace)) {
        includePaths.add(headerIncludePath(referenced));
      }
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray != null) {
        collectMessageIncludesForTypeRef(
            resolvedArray.elementTypeRef(), currentNamespace, generationContext, includePaths);
      }
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector != null) {
        collectMessageIncludesForTypeRef(
            resolvedVector.elementTypeRef(), currentNamespace, generationContext, includePaths);
      }
    }
  }

  /**
   * Appends method declarations for generated bitfield flag/segment helpers.
   *
   * @param builder destination header builder
   * @param messageType message being rendered
   */
  static void appendBitFieldHelperDeclarations(
      StringBuilder builder, ResolvedMessageType messageType) {
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedBitField resolvedBitField)) {
        continue;
      }
      String bitFieldPascal = toPascalCase(resolvedBitField.name());
      for (ResolvedBitFlag resolvedBitFlag : resolvedBitField.flags()) {
        String flagPascal = toPascalCase(resolvedBitFlag.name());
        builder
            .append("  bool get")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("() const;\n");
        builder
            .append("  void set")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("(bool value);\n");
      }
      for (ResolvedBitSegment resolvedBitSegment : resolvedBitField.segments()) {
        String segmentPascal = toPascalCase(resolvedBitSegment.name());
        builder
            .append("  std::uint64_t get")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("() const;\n");
        builder
            .append("  void set")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("(std::uint64_t value);\n");
        for (ResolvedBitVariant resolvedBitVariant : resolvedBitSegment.variants()) {
          builder
              .append("  static constexpr std::uint64_t ")
              .append(
                  toUpperSnakeCase(
                      resolvedBitField.name()
                          + "_"
                          + resolvedBitSegment.name()
                          + "_"
                          + resolvedBitVariant.name()))
              .append(" = ")
              .append(cppUnsignedLiteral(resolvedBitVariant.value()))
              .append(";\n");
        }
      }
      builder.append('\n');
    }
  }

  /**
   * Appends generated bitfield helper method definitions.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  static void appendBitFieldHelperDefinitions(
      StringBuilder builder, ResolvedMessageType messageType) {
    for (ResolvedMessageMember member : messageType.members()) {
      if (!(member instanceof ResolvedBitField resolvedBitField)) {
        continue;
      }
      String cppStorageType = bitFieldStorageCppType(resolvedBitField.size());
      String bitFieldName = resolvedBitField.name();
      String bitFieldPascal = toPascalCase(bitFieldName);
      for (ResolvedBitFlag resolvedBitFlag : resolvedBitField.flags()) {
        String flagPascal = toPascalCase(resolvedBitFlag.name());
        long mask = 1L << resolvedBitFlag.position();
        builder
            .append("bool ")
            .append(messageType.name())
            .append("::get")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("() const {\n")
            .append("  return (static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(") & ")
            .append(mask)
            .append("ULL) != 0ULL;\n")
            .append("}\n\n")
            .append("void ")
            .append(messageType.name())
            .append("::set")
            .append(bitFieldPascal)
            .append(flagPascal)
            .append("(bool value) {\n")
            .append("  std::uint64_t raw = static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(");\n")
            .append("  if (value) {\n")
            .append("    raw |= ")
            .append(mask)
            .append("ULL;\n")
            .append("  } else {\n")
            .append("    raw &= ~")
            .append(mask)
            .append("ULL;\n")
            .append("  }\n")
            .append("  ")
            .append(bitFieldName)
            .append(" = static_cast<")
            .append(cppStorageType)
            .append(">(raw);\n")
            .append("}\n\n");
      }

      for (ResolvedBitSegment resolvedBitSegment : resolvedBitField.segments()) {
        String segmentPascal = toPascalCase(resolvedBitSegment.name());
        int width = resolvedBitSegment.toBit() - resolvedBitSegment.fromBit() + 1;
        String maskLiteral = segmentMaskLiteral(width);
        String shiftedMaskLiteral = shiftedSegmentMaskLiteral(width, resolvedBitSegment.fromBit());
        builder
            .append("std::uint64_t ")
            .append(messageType.name())
            .append("::get")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("() const {\n")
            .append("  return (static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(") >> ")
            .append(resolvedBitSegment.fromBit())
            .append(") & ")
            .append(maskLiteral)
            .append(";\n")
            .append("}\n\n")
            .append("void ")
            .append(messageType.name())
            .append("::set")
            .append(bitFieldPascal)
            .append(segmentPascal)
            .append("(std::uint64_t value) {\n")
            .append("  if (value > ")
            .append(maskLiteral)
            .append(") {\n")
            .append("    throw std::invalid_argument(\"")
            .append(bitFieldName)
            .append('.')
            .append(resolvedBitSegment.name())
            .append(" is out of range for its bit segment.\");\n")
            .append("  }\n")
            .append("  std::uint64_t raw = static_cast<std::uint64_t>(")
            .append(bitFieldName)
            .append(");\n")
            .append("  raw = (raw & ~")
            .append(shiftedMaskLiteral)
            .append(") | ((value & ")
            .append(maskLiteral)
            .append(") << ")
            .append(resolvedBitSegment.fromBit())
            .append(");\n")
            .append("  ")
            .append(bitFieldName)
            .append(" = static_cast<")
            .append(cppStorageType)
            .append(">(raw);\n")
            .append("}\n\n");
      }
    }
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
   * Maps one bitfield storage size to the C++ field type used in generated helpers.
   *
   * @param bitFieldSize bitfield storage size
   * @return C++ type name for the raw storage field
   */
  private static String bitFieldStorageCppType(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> "std::uint8_t";
      case U16 -> "std::uint16_t";
      case U32 -> "std::uint32_t";
      case U64 -> "std::uint64_t";
    };
  }

  /**
   * Builds a C++ unsigned literal token for one 64-bit value.
   *
   * @param numericLiteral literal value
   * @return C++ token with {@code ULL} suffix
   */
  private static String cppUnsignedLiteral(BigInteger numericLiteral) {
    return numericLiteral.toString() + "ULL";
  }

  /**
   * Builds a segment mask literal for one bit width.
   *
   * @param width segment width in bits
   * @return C++ {@code ULL} literal mask
   */
  private static String segmentMaskLiteral(int width) {
    if (width >= 64) {
      return "0xFFFFFFFFFFFFFFFFULL";
    }
    return BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE).toString() + "ULL";
  }

  /**
   * Builds a shifted segment mask literal for one segment position.
   *
   * @param width segment width in bits
   * @param fromBit segment start bit
   * @return shifted C++ {@code ULL} literal mask
   */
  private static String shiftedSegmentMaskLiteral(int width, int fromBit) {
    if (width >= 64 && fromBit == 0) {
      return "0xFFFFFFFFFFFFFFFFULL";
    }
    return BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE).shiftLeft(fromBit).toString()
        + "ULL";
  }

  /**
   * Converts an identifier to pascal case for generated helper method names.
   *
   * @param value input identifier
   * @return pascal-case output
   */
  private static String toPascalCase(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      builder.append(Character.toUpperCase(part.charAt(0)));
      if (part.length() > 1) {
        builder.append(part.substring(1));
      }
    }
    return builder.toString();
  }

  /**
   * Converts a value to upper snake case for constant names.
   *
   * @param value input text
   * @return upper-snake output
   */
  private static String toUpperSnakeCase(String value) {
    String normalized = value.replaceAll("[^A-Za-z0-9]+", "_");
    return normalized.toUpperCase(Locale.ROOT);
  }
}
