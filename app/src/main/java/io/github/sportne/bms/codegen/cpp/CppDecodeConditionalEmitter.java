package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.ifConditionExpression;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.requiredChecksumRange;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedPad;

/** Emits C++ decode statements for conditional and control-flow members. */
final class CppDecodeConditionalEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppDecodeConditionalEmitter() {}

  /**
   * Appends pad decode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  static void appendDecodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("  requireReadable(data, cursor, ")
        .append(resolvedPad.bytes())
        .append("U, \"pad\");\n")
        .append("  cursor += ")
        .append(resolvedPad.bytes())
        .append("U;\n");
  }

  /**
   * Appends checksum decode statements.
   *
   * @param builder destination source builder
   * @param resolvedChecksum checksum definition
   */
  static void appendDecodeChecksum(StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRangeRules.ChecksumRange checksumRange =
        requiredChecksumRange(resolvedChecksum.range());
    String algorithm = resolvedChecksum.algorithm();
    builder
        .append("  {\n")
        .append("    std::span<const std::uint8_t> messageBytes(\n")
        .append("        data.data() + messageStartCursor,\n")
        .append("        data.size() - messageStartCursor);\n")
        .append("    validateChecksumRange(messageBytes.size(), ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(algorithm)
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n");
    if ("crc16".equals(algorithm)) {
      builder
          .append("    std::uint16_t expectedChecksum = readIntegral<std::uint16_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint16_t actualChecksum = crc16(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("    std::uint32_t expectedChecksum = readIntegral<std::uint32_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint32_t actualChecksum = crc32(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("    std::uint64_t expectedChecksum = readIntegral<std::uint64_t>(\n")
          .append("        data, cursor, false, \"")
          .append(algorithm)
          .append("_checksum\");\n")
          .append("    std::uint64_t actualChecksum = crc64(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("    requireReadable(data, cursor, 32U, \"sha256_checksum\");\n")
          .append("    std::array<std::uint8_t, 32> expectedChecksum{};\n")
          .append("    std::copy_n(data.begin() + static_cast<std::ptrdiff_t>(cursor), 32, ")
          .append("expectedChecksum.begin());\n")
          .append("    cursor += 32U;\n")
          .append("    std::array<std::uint8_t, 32> actualChecksum = sha256(messageBytes, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    if (expectedChecksum != actualChecksum) {\n")
          .append("      throw std::invalid_argument(\"Checksum mismatch for ")
          .append(algorithm)
          .append(" range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("    }\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("  }\n");
  }

  /**
   * Appends conditional-block decode statements.
   *
   * @param context shared decode emission context
   * @param resolvedIfBlock conditional block definition
   */
  static void appendDecodeIfBlock(CppDecodeContext context, ResolvedIfBlock resolvedIfBlock) {
    context
        .builder()
        .append("  if (")
        .append(ifConditionExpression(resolvedIfBlock.condition(), context.ownerPrefix()))
        .append(") {\n");
    CppDecodeEmitter.appendDecodeMembers(context, resolvedIfBlock.members());
    context.builder().append("  }\n");
  }
}
