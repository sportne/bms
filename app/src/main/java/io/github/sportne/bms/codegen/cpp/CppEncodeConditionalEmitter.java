package io.github.sportne.bms.codegen.cpp;

import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.ifConditionExpression;
import static io.github.sportne.bms.codegen.cpp.CppEmitterSupport.requiredChecksumRange;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedPad;

/** Emits C++ encode statements for conditional and control-flow members. */
final class CppEncodeConditionalEmitter {
  /** Prevents instantiation of this static helper class. */
  private CppEncodeConditionalEmitter() {}

  /**
   * Appends pad encode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  static void appendEncodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("  for (std::size_t padIndex = 0; padIndex < ")
        .append(resolvedPad.bytes())
        .append("U; padIndex++) {\n")
        .append("    out.push_back(0U);\n")
        .append("  }\n");
  }

  /**
   * Appends checksum encode statements.
   *
   * @param builder destination source builder
   * @param resolvedChecksum checksum definition
   */
  static void appendEncodeChecksum(StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRangeRules.ChecksumRange checksumRange =
        requiredChecksumRange(resolvedChecksum.range());
    String algorithm = resolvedChecksum.algorithm();
    builder
        .append("  {\n")
        .append("    validateChecksumRange(out.size(), ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(algorithm)
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n")
        .append("    std::span<const std::uint8_t> checksumSource(out.data(), out.size());\n");
    if ("crc16".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint16_t>(out, crc16(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint32_t>(out, crc32(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("    writeIntegral<std::uint64_t>(out, crc64(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append("), false);\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("    std::array<std::uint8_t, 32> checksumValue = sha256(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("    out.insert(out.end(), checksumValue.begin(), checksumValue.end());\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("  }\n");
  }

  /**
   * Appends conditional-block encode statements.
   *
   * @param context shared encode emission context
   * @param resolvedIfBlock conditional block definition
   */
  static void appendEncodeIfBlock(CppEncodeContext context, ResolvedIfBlock resolvedIfBlock) {
    context
        .builder()
        .append("  if (")
        .append(ifConditionExpression(resolvedIfBlock.condition(), context.ownerPrefix()))
        .append(") {\n");
    CppEncodeEmitter.appendEncodeMembers(context, resolvedIfBlock.members());
    context.builder().append("  }\n");
  }
}
