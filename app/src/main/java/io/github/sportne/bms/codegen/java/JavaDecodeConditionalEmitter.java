package io.github.sportne.bms.codegen.java;

import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.ifConditionExpression;
import static io.github.sportne.bms.codegen.java.JavaEmitterSupport.requiredChecksumRange;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedPad;

/** Emits Java decode statements for conditional and control-flow members. */
final class JavaDecodeConditionalEmitter {
  /** Prevents instantiation of this static helper class. */
  private JavaDecodeConditionalEmitter() {}

  /**
   * Appends pad decode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  static void appendDecodePad(StringBuilder builder, ResolvedPad resolvedPad) {
    builder
        .append("    input.position(input.position() + ")
        .append(resolvedPad.bytes())
        .append(");\n");
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
        .append("    {\n")
        .append("      validateChecksumRange(input.limit() - messageStartPosition, ")
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
          .append("      int expectedChecksum = readUInt16(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      int actualChecksum = crc16(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (expectedChecksum != actualChecksum) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc16 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + expectedChecksum + \", computed \" + actualChecksum + '.');\n")
          .append("      }\n");
    } else if ("crc32".equals(algorithm)) {
      builder
          .append("      long expectedChecksum = readUInt32(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      long actualChecksum = crc32(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (expectedChecksum != actualChecksum) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc32 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + expectedChecksum + \", computed \" + actualChecksum + '.');\n")
          .append("      }\n");
    } else if ("crc64".equals(algorithm)) {
      builder
          .append("      long expectedChecksum = readUInt64(input, ByteOrder.BIG_ENDIAN);\n")
          .append("      long actualChecksum = crc64(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (Long.compareUnsigned(expectedChecksum, actualChecksum) != 0) {\n")
          .append("        throw new IllegalArgumentException(\"Checksum mismatch for crc64 range ")
          .append(resolvedChecksum.range())
          .append(". Expected \" + Long.toUnsignedString(expectedChecksum)")
          .append(" + \", computed \" + Long.toUnsignedString(actualChecksum) + '.');\n")
          .append("      }\n");
    } else if ("sha256".equals(algorithm)) {
      builder
          .append("      byte[] expectedChecksum = new byte[32];\n")
          .append("      input.get(expectedChecksum);\n")
          .append("      byte[] actualChecksum = sha256(input, messageStartPosition, ")
          .append(checksumRange.startInclusive())
          .append(", ")
          .append(checksumRange.endInclusive())
          .append(");\n")
          .append("      if (!java.util.Arrays.equals(expectedChecksum, actualChecksum)) {\n")
          .append(
              "        throw new IllegalArgumentException(\"Checksum mismatch for sha256 range ")
          .append(resolvedChecksum.range())
          .append(".\");\n")
          .append("      }\n");
    } else {
      throw new IllegalStateException("Unsupported checksum algorithm: " + algorithm);
    }
    builder.append("    }\n");
  }

  /**
   * Appends conditional-block decode statements.
   *
   * @param context shared decode emission context
   * @param resolvedIfBlock if-block definition
   */
  static void appendDecodeIfBlock(JavaDecodeContext context, ResolvedIfBlock resolvedIfBlock) {
    String conditionExpression =
        ifConditionExpression(resolvedIfBlock.condition(), context.ownerPrefix());
    context.builder().append("    if (").append(conditionExpression).append(") {\n");
    JavaDecodeEmitter.appendDecodeMembers(context, resolvedIfBlock.members());
    context.builder().append("    }\n");
  }
}
