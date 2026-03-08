package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.ifConditionExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.requiredChecksumRange;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedPad;

/** Emits Java encode statements for conditional and control-flow members. */
final class JavaEncodeConditionalEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaEncodeConditionalEmitter() {}

  /**
   * Appends pad encode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  static void appendEncodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("    for (int padIndex = 0; padIndex < ")
        .append(resolvedPad.bytes())
        .append("; padIndex++) {\n")
        .append("      out.write(0);\n")
        .append("    }\n");
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
        .append("    {\n")
        .append("      byte[] checksumSource = out.toByteArray();\n")
        .append("      validateChecksumRange(checksumSource.length, ")
        .append(checksumRange.startInclusive())
        .append(", ")
        .append(checksumRange.endInclusive())
        .append(", \"")
        .append(resolvedChecksum.algorithm())
        .append("\", \"")
        .append(resolvedChecksum.range())
        .append("\");\n");

    if ("crc16".equals(algorithm)) {
      builder
          .append("      int checksumValue = crc16(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt16(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("      long checksumValue = crc32(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt32(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("      long checksumValue = crc64(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      writeUInt64(out, checksumValue, ByteOrder.BIG_ENDIAN);\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("      byte[] checksumValue = sha256(checksumSource, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      out.write(checksumValue, 0, checksumValue.length);\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("    }\n");
  }

  /**
   * Appends conditional-block encode statements.
   *
   * @param context shared encode emission context
   * @param resolvedIfBlock if-block definition
   */
  static void appendEncodeIfBlock(JavaEncodeContext context, ResolvedIfBlock resolvedIfBlock) {
    String conditionExpression =
        ifConditionExpression(resolvedIfBlock.condition(), context.ownerPrefix());
    context.builder().append("    if (").append(conditionExpression).append(") {\n");
    JavaEncodeEmitter.appendEncodeMembers(context, resolvedIfBlock.members());
    context.builder().append("    }\n");
  }
}
