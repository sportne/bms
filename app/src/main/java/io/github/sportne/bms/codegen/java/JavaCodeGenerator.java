package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfAndCondition;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfComparison;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.model.resolved.ResolvedIfOrCondition;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Generates Java source files from the resolved model.
 *
 * <p>Each message type becomes one Java class with deterministic field declarations plus
 * encode/decode logic. This generator currently supports foundation, numeric, collection, and
 * conditional members.
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

      /**
       * Validates a collection count value and converts it to an int.
       *
       * @param countValue decoded count value as long
       * @param fieldName field name shown in exception text
       * @return validated count value as int
       */
      private static int requireCount(long countValue, String fieldName) {
        if (countValue < 0 || countValue > Integer.MAX_VALUE) {
          throw new IllegalArgumentException(
              "Count field " + fieldName + " must be between 0 and Integer.MAX_VALUE.");
        }
        return (int) countValue;
      }

      /**
       * Validates an unsigned-64 count and converts it to an int.
       *
       * @param countValue raw unsigned-64 count bits
       * @param fieldName field name shown in exception text
       * @return validated count value as int
       */
      private static int requireCountUnsignedLong(long countValue, String fieldName) {
        if (Long.compareUnsigned(countValue, Integer.toUnsignedLong(Integer.MAX_VALUE)) > 0) {
          throw new IllegalArgumentException(
              "Unsigned count field "
                  + fieldName
                  + " must be <= Integer.MAX_VALUE. Received "
                  + Long.toUnsignedString(countValue)
                  + '.');
        }
        return (int) countValue;
      }

      /**
       * Converts a scaled logical value to a signed integer raw value.
       *
       * @param logicalValue logical floating-point value
       * @param scale scaling factor from the schema
       * @param minInclusive signed minimum raw value
       * @param maxInclusive signed maximum raw value
       * @param fieldName field/member name for exception text
       * @return rounded raw integer value
       */
      private static long scaleToSignedRaw(
          double logicalValue,
          double scale,
          long minInclusive,
          long maxInclusive,
          String fieldName) {
        if (!Double.isFinite(logicalValue)) {
          throw new IllegalArgumentException("Non-finite value for " + fieldName + '.');
        }
        if (scale == 0.0d || !Double.isFinite(scale)) {
          throw new IllegalArgumentException("Invalid scale for " + fieldName + '.');
        }

        double rawValue = logicalValue / scale;
        if (!Double.isFinite(rawValue)) {
          throw new IllegalArgumentException("Scaled value is not finite for " + fieldName + '.');
        }

        if (rawValue < minInclusive || rawValue > maxInclusive) {
          throw new IllegalArgumentException(
              "Value " + logicalValue + " is outside raw range for " + fieldName + '.');
        }

        long rounded = Math.round(rawValue);
        if (rounded < minInclusive || rounded > maxInclusive) {
          throw new IllegalArgumentException(
              "Rounded value for " + fieldName + " is outside raw range.");
        }
        return rounded;
      }

      /**
       * Converts a scaled logical value to an unsigned integer raw value.
       *
       * @param logicalValue logical floating-point value
       * @param scale scaling factor from the schema
       * @param maxInclusive unsigned maximum raw value that fits in a signed long
       * @param fieldName field/member name for exception text
       * @return rounded raw integer value
       */
      private static long scaleToUnsignedRaw(
          double logicalValue, double scale, long maxInclusive, String fieldName) {
        if (!Double.isFinite(logicalValue)) {
          throw new IllegalArgumentException("Non-finite value for " + fieldName + '.');
        }
        if (scale == 0.0d || !Double.isFinite(scale)) {
          throw new IllegalArgumentException("Invalid scale for " + fieldName + '.');
        }

        double rawValue = logicalValue / scale;
        if (!Double.isFinite(rawValue)) {
          throw new IllegalArgumentException("Scaled value is not finite for " + fieldName + '.');
        }
        if (rawValue < 0.0d || rawValue > maxInclusive) {
          throw new IllegalArgumentException(
              "Value " + logicalValue + " is outside unsigned raw range for " + fieldName + '.');
        }

        long rounded = Math.round(rawValue);
        if (rounded < 0 || rounded > maxInclusive) {
          throw new IllegalArgumentException(
              "Rounded value for " + fieldName + " is outside unsigned raw range.");
        }
        return rounded;
      }

      /**
       * Converts a scaled logical value to one unsigned 64-bit raw value.
       *
       * <p>The returned {@code long} stores the same 64 raw bits as the wire value.
       *
       * @param logicalValue logical floating-point value
       * @param scale scaling factor from the schema
       * @param fieldName field/member name for exception text
       * @return rounded raw unsigned-64 value encoded in one long
       */
      private static long scaleToUnsignedRaw64(double logicalValue, double scale, String fieldName) {
        if (!Double.isFinite(logicalValue)) {
          throw new IllegalArgumentException("Non-finite value for " + fieldName + '.');
        }
        if (scale == 0.0d || !Double.isFinite(scale)) {
          throw new IllegalArgumentException("Invalid scale for " + fieldName + '.');
        }

        double rawValue = logicalValue / scale;
        if (!Double.isFinite(rawValue)) {
          throw new IllegalArgumentException("Scaled value is not finite for " + fieldName + '.');
        }
        if (rawValue < 0.0d) {
          throw new IllegalArgumentException(
              "Value " + logicalValue + " is outside unsigned raw range for " + fieldName + '.');
        }

        java.math.BigDecimal roundedDecimal =
            java.math.BigDecimal.valueOf(rawValue).setScale(0, java.math.RoundingMode.HALF_UP);
        java.math.BigInteger roundedInteger;
        try {
          roundedInteger = roundedDecimal.toBigIntegerExact();
        } catch (ArithmeticException exception) {
          throw new IllegalArgumentException(
              "Rounded value for " + fieldName + " is outside unsigned raw range.");
        }

        java.math.BigInteger maxUnsigned = new java.math.BigInteger("18446744073709551615");
        if (roundedInteger.signum() < 0 || roundedInteger.compareTo(maxUnsigned) > 0) {
          throw new IllegalArgumentException(
              "Rounded value for " + fieldName + " is outside unsigned raw range.");
        }
        return roundedInteger.longValue();
      }

      /**
       * Converts one unsigned 64-bit raw value stored in a {@code long} to a {@code double}.
       *
       * @param value raw unsigned-64 value bits
       * @return floating-point value in the unsigned-64 numeric range
       */
      private static double unsignedLongToDouble(long value) {
        if (value >= 0L) {
          return (double) value;
        }
        return ((double) (value & Long.MAX_VALUE)) + 0x1.0p63;
      }
      """
          .indent(2)
          .replace("  \n", "\n");

  private static final String CHECKSUM_IO_HELPERS =
      """
      /**
       * Validates one checksum byte range against available source length.
       *
       * @param availableLength available byte count in the source
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @param algorithm checksum algorithm name
       * @param rangeText original range text used in exception messages
       */
      private static void validateChecksumRange(
          int availableLength,
          int rangeStart,
          int rangeEnd,
          String algorithm,
          String rangeText) {
        if (rangeStart < 0 || rangeEnd < rangeStart || rangeEnd >= availableLength) {
          throw new IllegalArgumentException(
              "Checksum "
                  + algorithm
                  + " range "
                  + rangeText
                  + " is out of bounds for "
                  + availableLength
                  + " available bytes.");
        }
      }

      /**
       * Computes CRC-16/CCITT-FALSE over one byte-array range.
       *
       * @param source source bytes
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 16-bit checksum value
       */
      private static int crc16(byte[] source, int rangeStart, int rangeEnd) {
        int crc = 0xFFFF;
        for (int index = rangeStart; index <= rangeEnd; index++) {
          crc ^= (source[index] & 0xFF) << 8;
          for (int bit = 0; bit < 8; bit++) {
            if ((crc & 0x8000) != 0) {
              crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
            } else {
              crc = (crc << 1) & 0xFFFF;
            }
          }
        }
        return crc & 0xFFFF;
      }

      /**
       * Computes CRC-32 over one byte-array range.
       *
       * @param source source bytes
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 32-bit checksum value
       */
      private static long crc32(byte[] source, int rangeStart, int rangeEnd) {
        CRC32 crc32 = new CRC32();
        for (int index = rangeStart; index <= rangeEnd; index++) {
          crc32.update(source[index] & 0xFF);
        }
        return crc32.getValue();
      }

      /**
       * Computes CRC-64/ECMA-182 over one byte-array range.
       *
       * @param source source bytes
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 64-bit checksum value
       */
      private static long crc64(byte[] source, int rangeStart, int rangeEnd) {
        long crc = 0L;
        for (int index = rangeStart; index <= rangeEnd; index++) {
          crc ^= (source[index] & 0xFFL) << 56;
          for (int bit = 0; bit < 8; bit++) {
            if ((crc & 0x8000000000000000L) != 0L) {
              crc = (crc << 1) ^ 0x42F0E1EBA9EA3693L;
            } else {
              crc = crc << 1;
            }
          }
        }
        return crc;
      }

      /**
       * Computes SHA-256 over one byte-array range.
       *
       * @param source source bytes
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 32-byte digest value
       */
      private static byte[] sha256(byte[] source, int rangeStart, int rangeEnd) {
        try {
          java.security.MessageDigest digest =
              java.security.MessageDigest.getInstance("SHA-256");
          digest.update(source, rangeStart, (rangeEnd - rangeStart) + 1);
          return digest.digest();
        } catch (java.security.NoSuchAlgorithmException exception) {
          throw new IllegalStateException(
              "SHA-256 digest is not available in this JDK.", exception);
        }
      }

      /**
       * Computes CRC-16/CCITT-FALSE over one byte-buffer range.
       *
       * @param input source byte buffer
       * @param messageStartPosition start index of the decoded message
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 16-bit checksum value
       */
      private static int crc16(
          ByteBuffer input, int messageStartPosition, int rangeStart, int rangeEnd) {
        int absoluteStart = messageStartPosition + rangeStart;
        int absoluteEnd = messageStartPosition + rangeEnd;
        int crc = 0xFFFF;
        for (int index = absoluteStart; index <= absoluteEnd; index++) {
          crc ^= (input.get(index) & 0xFF) << 8;
          for (int bit = 0; bit < 8; bit++) {
            if ((crc & 0x8000) != 0) {
              crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
            } else {
              crc = (crc << 1) & 0xFFFF;
            }
          }
        }
        return crc & 0xFFFF;
      }

      /**
       * Computes CRC-32 over one byte-buffer range.
       *
       * @param input source byte buffer
       * @param messageStartPosition start index of the decoded message
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 32-bit checksum value
       */
      private static long crc32(
          ByteBuffer input, int messageStartPosition, int rangeStart, int rangeEnd) {
        int absoluteStart = messageStartPosition + rangeStart;
        int absoluteEnd = messageStartPosition + rangeEnd;
        CRC32 crc32 = new CRC32();
        for (int index = absoluteStart; index <= absoluteEnd; index++) {
          crc32.update(input.get(index) & 0xFF);
        }
        return crc32.getValue();
      }

      /**
       * Computes CRC-64/ECMA-182 over one byte-buffer range.
       *
       * @param input source byte buffer
       * @param messageStartPosition start index of the decoded message
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 64-bit checksum value
       */
      private static long crc64(
          ByteBuffer input, int messageStartPosition, int rangeStart, int rangeEnd) {
        int absoluteStart = messageStartPosition + rangeStart;
        int absoluteEnd = messageStartPosition + rangeEnd;
        long crc = 0L;
        for (int index = absoluteStart; index <= absoluteEnd; index++) {
          crc ^= (input.get(index) & 0xFFL) << 56;
          for (int bit = 0; bit < 8; bit++) {
            if ((crc & 0x8000000000000000L) != 0L) {
              crc = (crc << 1) ^ 0x42F0E1EBA9EA3693L;
            } else {
              crc = crc << 1;
            }
          }
        }
        return crc;
      }

      /**
       * Computes SHA-256 over one byte-buffer range.
       *
       * @param input source byte buffer
       * @param messageStartPosition start index of the decoded message
       * @param rangeStart first checksum byte index (inclusive)
       * @param rangeEnd last checksum byte index (inclusive)
       * @return computed 32-byte digest value
       */
      private static byte[] sha256(
          ByteBuffer input, int messageStartPosition, int rangeStart, int rangeEnd) {
        int absoluteStart = messageStartPosition + rangeStart;
        int absoluteEnd = messageStartPosition + rangeEnd;
        try {
          java.security.MessageDigest digest =
              java.security.MessageDigest.getInstance("SHA-256");
          ByteBuffer slice = input.duplicate();
          slice.position(absoluteStart);
          slice.limit(absoluteEnd + 1);
          digest.update(slice);
          return digest.digest();
        } catch (java.security.NoSuchAlgorithmException exception) {
          throw new IllegalStateException(
              "SHA-256 digest is not available in this JDK.", exception);
        }
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
    GenerationContext generationContext = buildGenerationContext(schema);

    for (ResolvedMessageType messageType : schema.messageTypes()) {
      Path packageDirectory =
          outputDirectory.resolve(messageType.effectiveNamespace().replace('.', '/'));
      Path outputPath = packageDirectory.resolve(messageType.name() + ".java");

      ensureSupportedMembers(messageType, generationContext, outputPath);

      String source = renderMessageType(messageType, generationContext);
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
   * Builds lookup maps used during Java generation.
   *
   * @param schema resolved schema used for generation
   * @return generation context with deterministic lookup maps
   */
  private static GenerationContext buildGenerationContext(ResolvedSchema schema) {
    return new GenerationContext(
        mapMessages(schema.messageTypes()),
        mapFloats(schema.reusableFloats()),
        mapScaledInts(schema.reusableScaledInts()),
        mapArrays(schema.reusableArrays()),
        mapVectors(schema.reusableVectors()),
        mapBlobArrays(schema.reusableBlobArrays()),
        mapBlobVectors(schema.reusableBlobVectors()),
        mapVarStrings(schema.reusableVarStrings()));
  }

  /**
   * Builds a stable message-name lookup map.
   *
   * @param messageTypes resolved message types
   * @return immutable message lookup map
   */
  private static Map<String, ResolvedMessageType> mapMessages(
      List<ResolvedMessageType> messageTypes) {
    Map<String, ResolvedMessageType> messageTypeByName = new LinkedHashMap<>();
    for (ResolvedMessageType messageType : messageTypes) {
      messageTypeByName.put(messageType.name(), messageType);
    }
    return Map.copyOf(messageTypeByName);
  }

  /**
   * Builds a stable reusable-float lookup map.
   *
   * @param reusableFloats reusable float definitions
   * @return immutable float lookup map
   */
  private static Map<String, ResolvedFloat> mapFloats(List<ResolvedFloat> reusableFloats) {
    Map<String, ResolvedFloat> reusableFloatByName = new LinkedHashMap<>();
    for (ResolvedFloat resolvedFloat : reusableFloats) {
      reusableFloatByName.put(resolvedFloat.name(), resolvedFloat);
    }
    return Map.copyOf(reusableFloatByName);
  }

  /**
   * Builds a stable reusable-scaledInt lookup map.
   *
   * @param reusableScaledInts reusable scaledInt definitions
   * @return immutable scaledInt lookup map
   */
  private static Map<String, ResolvedScaledInt> mapScaledInts(
      List<ResolvedScaledInt> reusableScaledInts) {
    Map<String, ResolvedScaledInt> reusableScaledIntByName = new LinkedHashMap<>();
    for (ResolvedScaledInt resolvedScaledInt : reusableScaledInts) {
      reusableScaledIntByName.put(resolvedScaledInt.name(), resolvedScaledInt);
    }
    return Map.copyOf(reusableScaledIntByName);
  }

  /**
   * Builds a stable reusable-array lookup map.
   *
   * @param reusableArrays reusable array definitions
   * @return immutable array lookup map
   */
  private static Map<String, ResolvedArray> mapArrays(List<ResolvedArray> reusableArrays) {
    Map<String, ResolvedArray> reusableArrayByName = new LinkedHashMap<>();
    for (ResolvedArray resolvedArray : reusableArrays) {
      reusableArrayByName.put(resolvedArray.name(), resolvedArray);
    }
    return Map.copyOf(reusableArrayByName);
  }

  /**
   * Builds a stable reusable-vector lookup map.
   *
   * @param reusableVectors reusable vector definitions
   * @return immutable vector lookup map
   */
  private static Map<String, ResolvedVector> mapVectors(List<ResolvedVector> reusableVectors) {
    Map<String, ResolvedVector> reusableVectorByName = new LinkedHashMap<>();
    for (ResolvedVector resolvedVector : reusableVectors) {
      reusableVectorByName.put(resolvedVector.name(), resolvedVector);
    }
    return Map.copyOf(reusableVectorByName);
  }

  /**
   * Builds a stable reusable-blobArray lookup map.
   *
   * @param reusableBlobArrays reusable blobArray definitions
   * @return immutable blobArray lookup map
   */
  private static Map<String, ResolvedBlobArray> mapBlobArrays(
      List<ResolvedBlobArray> reusableBlobArrays) {
    Map<String, ResolvedBlobArray> reusableBlobArrayByName = new LinkedHashMap<>();
    for (ResolvedBlobArray resolvedBlobArray : reusableBlobArrays) {
      reusableBlobArrayByName.put(resolvedBlobArray.name(), resolvedBlobArray);
    }
    return Map.copyOf(reusableBlobArrayByName);
  }

  /**
   * Builds a stable reusable-blobVector lookup map.
   *
   * @param reusableBlobVectors reusable blobVector definitions
   * @return immutable blobVector lookup map
   */
  private static Map<String, ResolvedBlobVector> mapBlobVectors(
      List<ResolvedBlobVector> reusableBlobVectors) {
    Map<String, ResolvedBlobVector> reusableBlobVectorByName = new LinkedHashMap<>();
    for (ResolvedBlobVector resolvedBlobVector : reusableBlobVectors) {
      reusableBlobVectorByName.put(resolvedBlobVector.name(), resolvedBlobVector);
    }
    return Map.copyOf(reusableBlobVectorByName);
  }

  /**
   * Builds a stable reusable-varString lookup map.
   *
   * @param reusableVarStrings reusable varString definitions
   * @return immutable varString lookup map
   */
  private static Map<String, ResolvedVarString> mapVarStrings(
      List<ResolvedVarString> reusableVarStrings) {
    Map<String, ResolvedVarString> reusableVarStringByName = new LinkedHashMap<>();
    for (ResolvedVarString resolvedVarString : reusableVarStrings) {
      reusableVarStringByName.put(resolvedVarString.name(), resolvedVarString);
    }
    return Map.copyOf(reusableVarStringByName);
  }

  /**
   * Verifies that this generator supports every member and type used by the message.
   *
   * @param messageType message being generated
   * @param generationContext reusable lookup maps used for validation
   * @param outputPath output file path used in diagnostics
   * @throws BmsException if unsupported members or references are found
   */
  private static void ensureSupportedMembers(
      ResolvedMessageType messageType, GenerationContext generationContext, Path outputPath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    Set<String> flattenedMemberNames = new java.util.LinkedHashSet<>();

    checkFlattenedMemberNames(
        messageType.name(),
        messageType.members(),
        flattenedMemberNames,
        outputPath,
        diagnostics,
        "message");

    for (ResolvedMessageMember member : messageType.members()) {
      checkMemberSupport(
          member, messageType, generationContext, primitiveFieldByName, outputPath, diagnostics);
    }

    if (!diagnostics.isEmpty()) {
      throw new BmsException(
          "Java code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a map from primitive field name to primitive type for one message.
   *
   * @param messageType resolved message type
   * @return primitive field lookup map
   */
  private static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    Map<String, PrimitiveType> primitiveFieldByName = new LinkedHashMap<>();
    collectPrimitiveFields(primitiveFieldByName, messageType.members());
    return Map.copyOf(primitiveFieldByName);
  }

  /**
   * Collects primitive scalar field types from message members recursively.
   *
   * @param primitiveFieldByName destination primitive-field lookup map
   * @param members members to scan
   */
  private static void collectPrimitiveFields(
      Map<String, PrimitiveType> primitiveFieldByName, List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedField resolvedField
          && resolvedField.typeRef() instanceof PrimitiveTypeRef primitiveTypeRef) {
        primitiveFieldByName.putIfAbsent(resolvedField.name(), primitiveTypeRef.primitiveType());
        continue;
      }
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        collectPrimitiveFields(primitiveFieldByName, resolvedIfBlock.members());
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        collectPrimitiveFields(primitiveFieldByName, resolvedNestedType.members());
      }
    }
  }

  /**
   * Checks support for one message member.
   *
   * @param member message member to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkMemberSupport(
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (member instanceof ResolvedField resolvedField) {
      checkFieldTypeSupport(
          resolvedField,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBitField) {
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      checkScaledIntSupport(resolvedScaledInt, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBlobArray) {
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      checkBlobVectorSupport(
          resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      checkVarStringSupport(
          resolvedVarString, messageType, primitiveFieldByName, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedPad) {
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      checkChecksumSupport(resolvedChecksum, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      checkIfBlockSupport(
          resolvedIfBlock,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedMessageType) {
      checkNestedTypeSupport(
          resolvedNestedMessageType,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(), member.getClass().getSimpleName(), outputPath.toString()));
  }

  /**
   * Checks support for checksum members.
   *
   * @param resolvedChecksum checksum definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkChecksumSupport(
      ResolvedChecksum resolvedChecksum,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!isSupportedChecksumAlgorithm(resolvedChecksum.algorithm())) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(unsupported algorithm)",
              outputPath.toString()));
      return;
    }

    if (parseChecksumRange(resolvedChecksum.range()) == null) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(invalid range)",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for conditional blocks and recursively validates contained members.
   *
   * @param resolvedIfBlock conditional block to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkIfBlockSupport(
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    for (ResolvedMessageMember nestedMember : resolvedIfBlock.members()) {
      checkMemberSupport(
          nestedMember,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
    }
  }

  /**
   * Checks support for nested message definitions and recursively validates contained members.
   *
   * @param resolvedNestedType nested message definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkNestedTypeSupport(
      ResolvedMessageType resolvedNestedType,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    for (ResolvedMessageMember nestedMember : resolvedNestedType.members()) {
      checkMemberSupport(
          nestedMember,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
    }
  }

  /**
   * Rejects flattened member-name collisions that would produce duplicate Java fields.
   *
   * @param ownerName parent message name used in diagnostics
   * @param members members to scan
   * @param flattenedMemberNames destination set of generated field names
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerContext short owner label used in diagnostic text
   */
  private static void checkFlattenedMemberNames(
      String ownerName,
      List<ResolvedMessageMember> members,
      Set<String> flattenedMemberNames,
      Path outputPath,
      List<Diagnostic> diagnostics,
      String ownerContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        checkFlattenedMemberNames(
            ownerName,
            resolvedIfBlock.members(),
            flattenedMemberNames,
            outputPath,
            diagnostics,
            "if block");
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        checkFlattenedMemberNames(
            ownerName,
            resolvedNestedType.members(),
            flattenedMemberNames,
            outputPath,
            diagnostics,
            "nested type " + resolvedNestedType.name());
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      String flattenedName = memberName(member);
      if (!flattenedMemberNames.add(flattenedName)) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                ownerName,
                ownerContext + " member name collision for " + flattenedName,
                outputPath.toString()));
      }
    }
  }

  /**
   * Returns whether one member kind produces a generated Java field declaration.
   *
   * @param member member to inspect
   * @return {@code true} when the member is emitted as a Java field
   */
  private static boolean isDeclarableMember(ResolvedMessageMember member) {
    return member instanceof ResolvedField
        || member instanceof ResolvedBitField
        || member instanceof ResolvedFloat
        || member instanceof ResolvedScaledInt
        || member instanceof ResolvedArray
        || member instanceof ResolvedVector
        || member instanceof ResolvedBlobArray
        || member instanceof ResolvedBlobVector
        || member instanceof ResolvedVarString;
  }

  /**
   * Returns whether one checksum algorithm is implemented in this milestone.
   *
   * @param algorithm checksum algorithm literal from the resolved model
   * @return {@code true} when encode/decode logic exists for the algorithm
   */
  private static boolean isSupportedChecksumAlgorithm(String algorithm) {
    return "crc16".equals(algorithm)
        || "crc32".equals(algorithm)
        || "crc64".equals(algorithm)
        || "sha256".equals(algorithm);
  }

  /**
   * Checks support for a resolved field member and its referenced type.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFieldTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    if (typeRef instanceof PrimitiveTypeRef || typeRef instanceof MessageTypeRef) {
      return;
    }

    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      if (resolvedFloat == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable float was not found: " + floatTypeRef.floatTypeName()));
        return;
      }
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      if (resolvedScaledInt == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable scaledInt was not found: " + scaledIntTypeRef.scaledIntTypeName()));
        return;
      }
      checkScaledIntSupport(resolvedScaledInt, messageType, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable array was not found: " + arrayTypeRef.arrayTypeName()));
        return;
      }
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable vector was not found: " + vectorTypeRef.vectorTypeName()));
        return;
      }
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }

    if (typeRef instanceof BlobArrayTypeRef
        || typeRef instanceof BlobVectorTypeRef
        || typeRef instanceof VarStringTypeRef) {
      checkBlobOrVarStringTypeSupport(
          resolvedField,
          messageType,
          generationContext,
          primitiveFieldByName,
          typeRef,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedTypeRefDiagnostic(
            messageType.name(),
            resolvedField.name(),
            typeRef.getClass().getSimpleName(),
            outputPath.toString(),
            "This type reference is not implemented in the Java backend yet."));
  }

  /**
   * Checks support for blob and varString field type references.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param typeRef resolved type reference being checked
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobOrVarStringTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      ResolvedTypeRef typeRef,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      if (!generationContext
          .reusableBlobArrayByName()
          .containsKey(blobArrayTypeRef.blobArrayTypeName())) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable blobArray was not found: " + blobArrayTypeRef.blobArrayTypeName()));
      }
      return;
    }

    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      if (resolvedBlobVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable blobVector was not found: " + blobVectorTypeRef.blobVectorTypeName()));
      } else {
        checkBlobVectorSupport(
            resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      }
      return;
    }

    VarStringTypeRef varStringTypeRef = (VarStringTypeRef) typeRef;
    ResolvedVarString resolvedVarString =
        generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
    if (resolvedVarString == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath.toString(),
              "Reusable varString was not found: " + varStringTypeRef.varStringTypeName()));
      return;
    }
    checkVarStringSupport(
        resolvedVarString, messageType, primitiveFieldByName, outputPath, diagnostics);
  }

  /**
   * Checks support for one float definition.
   *
   * @param resolvedFloat float definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFloatSupport(
      ResolvedFloat resolvedFloat,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F16) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F32) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F64) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedFloat(" + resolvedFloat.encoding() + ", " + resolvedFloat.size() + ")",
            outputPath.toString()));
  }

  /**
   * Checks support for one scaled-int definition.
   *
   * @param resolvedScaledInt scaledInt definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkScaledIntSupport(
      ResolvedScaledInt resolvedScaledInt,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {}

  /**
   * Checks support for one array definition.
   *
   * @param resolvedArray array definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkArraySupport(
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedArray.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedArray(elementType="
                  + resolvedArray.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for one vector definition.
   *
   * @param resolvedVector vector definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVectorSupport(
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedVector.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedVector(elementType="
                  + resolvedVector.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath.toString()));
      return;
    }

    checkLengthModeSupport(
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "vector " + resolvedVector.name());
  }

  /**
   * Checks support for one blob-vector definition.
   *
   * @param resolvedBlobVector blob-vector definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobVectorSupport(
      ResolvedBlobVector resolvedBlobVector,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    checkLengthModeSupport(
        resolvedBlobVector.lengthMode(),
        new PrimitiveTypeRef(PrimitiveType.UINT8),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "blobVector " + resolvedBlobVector.name());
  }

  /**
   * Checks support for one varString definition.
   *
   * @param resolvedVarString varString definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVarStringSupport(
      ResolvedVarString resolvedVarString,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                "varString "
                    + resolvedVarString.name()
                    + "(countField ref=\""
                    + resolvedCountFieldLength.ref()
                    + "\")",
                outputPath.toString()));
      }
      return;
    }

    if (resolvedVarString.lengthMode()
        instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      validateVarStringTerminatorLiteral(
          resolvedVarString,
          resolvedTerminatorValueLength.value(),
          messageType,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "varString " + resolvedVarString.name() + "(terminatorField path)",
            outputPath.toString()));
  }

  /**
   * Checks that one varString terminator literal is supported.
   *
   * @param resolvedVarString varString definition to validate
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void validateVarStringTerminatorLiteral(
      ResolvedVarString resolvedVarString,
      String literal,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    try {
      BigInteger numericLiteral = parseNumericLiteral(literal);
      if (!fitsPrimitiveRange(numericLiteral, PrimitiveType.UINT8)) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                "varString "
                    + resolvedVarString.name()
                    + "(terminator literal out of range: "
                    + literal
                    + ")",
                outputPath.toString()));
      }
    } catch (NumberFormatException exception) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "varString "
                  + resolvedVarString.name()
                  + "(invalid terminator literal: "
                  + literal
                  + ")",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for one length mode.
   *
   * @param lengthMode length mode to validate
   * @param elementTypeRef vector/blob element type
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName owner name used in diagnostic messages
   */
  private static void checkLengthModeSupport(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(countField ref=\"" + resolvedCountFieldLength.ref() + "\")",
                outputPath.toString()));
      }
      return;
    }

    if (lengthMode instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      validatePrimitiveTerminatorLiteral(
          elementTypeRef,
          resolvedTerminatorValueLength.value(),
          messageType,
          outputPath,
          diagnostics,
          ownerName);
      return;
    }

    String terminatorLiteral = terminatorLiteral(((ResolvedTerminatorField) lengthMode).next());
    if (terminatorLiteral == null) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminatorField path missing terminatorMatch)",
              outputPath.toString()));
      return;
    }

    validatePrimitiveTerminatorLiteral(
        elementTypeRef, terminatorLiteral, messageType, outputPath, diagnostics, ownerName);
  }

  /**
   * Checks that a terminator literal is supported for a primitive element type.
   *
   * @param elementTypeRef element type of the vector/blob
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName owner name used in diagnostic messages
   */
  private static void validatePrimitiveTerminatorLiteral(
      ResolvedTypeRef elementTypeRef,
      String literal,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (!(elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminator modes require primitive element types)",
              outputPath.toString()));
      return;
    }

    try {
      BigInteger numericLiteral = parseNumericLiteral(literal);
      if (!fitsPrimitiveRange(numericLiteral, primitiveTypeRef.primitiveType())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(terminator literal out of range: " + literal + ")",
                outputPath.toString()));
      }
    } catch (NumberFormatException exception) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(invalid terminator literal: " + literal + ")",
              outputPath.toString()));
    }
  }

  /**
   * Determines whether a collection element type is supported in this backend milestone.
   *
   * @param elementTypeRef element type reference to check
   * @param generationContext reusable lookup maps
   * @return {@code true} when the element type can be emitted
   */
  private static boolean isCollectionElementTypeSupported(
      ResolvedTypeRef elementTypeRef, GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef || elementTypeRef instanceof MessageTypeRef) {
      return true;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      return generationContext.reusableFloatByName().containsKey(floatTypeRef.floatTypeName());
    }

    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      return generationContext
          .reusableScaledIntByName()
          .containsKey(scaledIntTypeRef.scaledIntTypeName());
    }

    return false;
  }

  /**
   * Creates one unsupported-member diagnostic.
   *
   * @param messageName parent message name
   * @param memberLabel unsupported member label
   * @param outputPath output path shown in diagnostics
   * @return unsupported-member diagnostic
   */
  private static Diagnostic unsupportedMemberDiagnostic(
      String messageName, String memberLabel, String outputPath) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_JAVA_UNSUPPORTED_MEMBER",
        "Java generator does not support message member "
            + memberLabel
            + " in message "
            + messageName
            + " yet.",
        outputPath,
        -1,
        -1);
  }

  /**
   * Creates one unsupported-type-reference diagnostic.
   *
   * @param messageName parent message name
   * @param fieldName field name that owns the reference
   * @param referenceKind reference kind label
   * @param outputPath output path shown in diagnostics
   * @param reason additional reason text
   * @return unsupported-type-reference diagnostic
   */
  private static Diagnostic unsupportedTypeRefDiagnostic(
      String messageName,
      String fieldName,
      String referenceKind,
      String outputPath,
      String reason) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_JAVA_UNSUPPORTED_TYPE_REF",
        "Java generator does not support field type reference "
            + referenceKind
            + " for field "
            + fieldName
            + " in message "
            + messageName
            + " yet. "
            + reason,
        outputPath,
        -1,
        -1);
  }

  /**
   * Renders one complete Java class source file.
   *
   * @param messageType message type to render
   * @param generationContext reusable lookup maps
   * @return generated Java source text
   */
  private static String renderMessageType(
      ResolvedMessageType messageType, GenerationContext generationContext) {
    StringBuilder builder = new StringBuilder();

    builder.append("package ").append(messageType.effectiveNamespace()).append(";\n\n");
    appendImports(builder, messageType, generationContext);

    builder.append("public final class ").append(messageType.name()).append(" {\n");

    appendMemberDeclarations(builder, messageType, generationContext);
    appendBitFieldHelpers(builder, messageType);
    appendEncodeMethod(builder, messageType, generationContext);
    appendDecodeMethods(builder, messageType, generationContext);

    builder.append(SHARED_IO_HELPERS);
    if (containsChecksumMember(messageType.members())) {
      builder.append(CHECKSUM_IO_HELPERS);
    }
    builder.append("}\n");

    return builder.toString();
  }

  /**
   * Appends import lines required by one generated message class.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendImports(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
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
  private static boolean containsChecksumMember(List<ResolvedMessageMember> members) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedChecksum) {
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
      Set<String> imports, ResolvedMessageType messageType, GenerationContext generationContext) {
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
      GenerationContext generationContext) {
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
   * Collects imports required by one type reference.
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
      GenerationContext generationContext) {
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
   * Appends field declarations for message members.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendMemberDeclarations(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    appendMemberDeclarationsForMembers(builder, messageType.members(), generationContext);
    builder.append("\n");
  }

  /**
   * Appends field declarations for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to render
   * @param generationContext reusable lookup maps
   */
  private static void appendMemberDeclarationsForMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      GenerationContext generationContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        appendMemberDeclarationsForMembers(builder, resolvedIfBlock.members(), generationContext);
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        appendMemberDeclarationsForMembers(
            builder, resolvedNestedType.members(), generationContext);
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      builder
          .append("  public ")
          .append(javaTypeForMember(member, generationContext))
          .append(' ')
          .append(memberName(member))
          .append(";\n");
    }
  }

  /**
   * Resolves Java declaration type for one member.
   *
   * @param member member to inspect
   * @param generationContext reusable lookup maps
   * @return Java declaration type
   */
  private static String javaTypeForMember(
      ResolvedMessageMember member, GenerationContext generationContext) {
    if (member instanceof ResolvedField resolvedField) {
      return javaTypeForTypeRef(resolvedField.typeRef(), generationContext);
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return javaTypeForBitFieldSize(resolvedBitField.size());
    }
    if (member instanceof ResolvedFloat || member instanceof ResolvedScaledInt) {
      return "double";
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext) + "[]";
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext)
          + "[]";
    }
    if (member instanceof ResolvedBlobArray || member instanceof ResolvedBlobVector) {
      return "byte[]";
    }
    if (member instanceof ResolvedVarString) {
      return "String";
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Resolves Java declaration type for one type reference.
   *
   * @param typeRef type reference to inspect
   * @param generationContext reusable lookup maps
   * @return Java declaration type
   */
  private static String javaTypeForTypeRef(
      ResolvedTypeRef typeRef, GenerationContext generationContext) {
    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().javaTypeName();
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      return referenced == null ? messageTypeRef.messageTypeName() : referenced.name();
    }
    if (typeRef instanceof FloatTypeRef || typeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        throw new IllegalStateException("Missing reusable array: " + arrayTypeRef.arrayTypeName());
      }
      return javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext) + "[]";
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        throw new IllegalStateException(
            "Missing reusable vector: " + vectorTypeRef.vectorTypeName());
      }
      return javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext)
          + "[]";
    }
    if (typeRef instanceof BlobArrayTypeRef || typeRef instanceof BlobVectorTypeRef) {
      return "byte[]";
    }
    if (typeRef instanceof VarStringTypeRef) {
      return "String";
    }
    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Resolves Java element type for collection members.
   *
   * @param elementTypeRef collection element type reference
   * @param generationContext reusable lookup maps
   * @return Java type used for one collection element
   */
  private static String javaElementTypeForCollection(
      ResolvedTypeRef elementTypeRef, GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      return primitiveTypeRef.primitiveType().javaTypeName();
    }
    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      ResolvedMessageType referenced =
          generationContext.messageTypeByName().get(messageTypeRef.messageTypeName());
      return referenced == null ? messageTypeRef.messageTypeName() : referenced.name();
    }
    if (elementTypeRef instanceof FloatTypeRef || elementTypeRef instanceof ScaledIntTypeRef) {
      return "double";
    }
    throw new IllegalStateException(
        "Unsupported collection element type: " + elementTypeRef.getClass().getSimpleName());
  }

  /**
   * Resolves one member name.
   *
   * @param member member to inspect
   * @return member name
   */
  private static String memberName(ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      return resolvedField.name();
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return resolvedBitField.name();
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      return resolvedFloat.name();
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      return resolvedScaledInt.name();
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return resolvedArray.name();
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return resolvedVector.name();
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return resolvedBlobArray.name();
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      return resolvedBlobVector.name();
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      return resolvedVarString.name();
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Resolves Java declaration type for bitField storage size.
   *
   * @param bitFieldSize bitField size
   * @return Java type used to store the raw bit container
   */
  private static String javaTypeForBitFieldSize(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> "short";
      case U16 -> "int";
      case U32 -> "long";
      case U64 -> "long";
    };
  }

  /**
   * Appends helper methods for each bitField member.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   */
  private static void appendBitFieldHelpers(
      StringBuilder builder, ResolvedMessageType messageType) {
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

  /**
   * Appends encode method implementation.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeMethod(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);

    builder.append("  public byte[] encode() {\n");
    builder.append("    ByteArrayOutputStream out = new ByteArrayOutputStream();\n");

    appendEncodeMembers(
        builder, messageType.members(), messageType, generationContext, primitiveFieldByName);

    builder.append("    return out.toByteArray();\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends encode statements for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    for (ResolvedMessageMember member : members) {
      appendEncodeMember(builder, member, messageType, generationContext, primitiveFieldByName);
    }
  }

  /**
   * Appends encode statements for one member.
   *
   * @param builder destination source builder
   * @param member member to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    if (member instanceof ResolvedField resolvedField) {
      appendEncodeField(
          builder, resolvedField, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendEncodeBitField(builder, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendEncodeFloat(
          builder, "this." + resolvedFloat.name(), resolvedFloat, resolvedFloat.name());
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendEncodeScaledInt(
          builder, "this." + resolvedScaledInt.name(), resolvedScaledInt, resolvedScaledInt.name());
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendEncodeArray(
          builder,
          "this." + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name(),
          generationContext);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendEncodeVector(
          builder,
          "this." + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name(),
          generationContext,
          primitiveFieldByName,
          "this.");
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendEncodeBlobArray(
          builder, "this." + resolvedBlobArray.name(), resolvedBlobArray, resolvedBlobArray.name());
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendEncodeBlobVector(
          builder,
          "this." + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name(),
          primitiveFieldByName,
          "this.");
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendEncodeVarString(
          builder,
          "this." + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name(),
          primitiveFieldByName,
          "this.");
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      appendEncodePad(builder, resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      appendEncodeChecksum(builder, resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      appendEncodeIfBlock(
          builder, resolvedIfBlock, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendEncodeMembers(
          builder,
          resolvedNestedType.members(),
          messageType,
          generationContext,
          primitiveFieldByName);
      return;
    }

    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Appends encode statements for one field member.
   *
   * @param builder destination source builder
   * @param resolvedField field member to encode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeField(
      StringBuilder builder,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String fieldExpression = "this." + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveEncode(
          builder, fieldExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      appendMessageEncode(builder, fieldExpression, resolvedField.name(), javaType);
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, fieldExpression, resolvedFloat, resolvedField.name());
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendEncodeScaledInt(builder, fieldExpression, resolvedScaledInt, resolvedField.name());
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendEncodeArray(
          builder, fieldExpression, resolvedArray, resolvedField.name(), generationContext);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendEncodeVector(
          builder,
          fieldExpression,
          resolvedVector,
          resolvedField.name(),
          generationContext,
          primitiveFieldByName,
          "this.");
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      appendEncodeBlobArray(builder, fieldExpression, resolvedBlobArray, resolvedField.name());
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendEncodeBlobVector(
          builder,
          fieldExpression,
          resolvedBlobVector,
          resolvedField.name(),
          primitiveFieldByName,
          "this.");
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
      appendEncodeVarString(
          builder,
          fieldExpression,
          resolvedVarString,
          resolvedField.name(),
          primitiveFieldByName,
          "this.");
      return;
    }

    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the value to encode
   * @param primitiveType primitive type of the value
   * @param endian optional endian override
   */
  private static void appendPrimitiveEncode(
      StringBuilder builder, String valueExpression, PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    switch (primitiveType) {
      case UINT8 -> builder.append("    writeUInt8(out, ").append(valueExpression).append(");\n");
      case UINT16 -> builder
          .append("    writeUInt16(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    writeUInt32(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    writeUInt64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT8 -> builder.append("    writeInt8(out, ").append(valueExpression).append(");\n");
      case INT16 -> builder
          .append("    writeInt16(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    writeInt32(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    writeInt64(out, ")
          .append(valueExpression)
          .append(", ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends nested-message encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the nested message value
   * @param fieldName field name used in null-check text
   * @param javaType nested Java type name used to build temp variable names
   */
  private static void appendMessageEncode(
      StringBuilder builder, String valueExpression, String fieldName, String javaType) {
    String variableSuffix = toPascalCase(javaType);
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    byte[] encoded")
        .append(variableSuffix)
        .append(" = ")
        .append(valueExpression)
        .append(".encode();\n")
        .append("    out.write(encoded")
        .append(variableSuffix)
        .append(", 0, encoded")
        .append(variableSuffix)
        .append(".length);\n");
  }

  /**
   * Appends bitField encode statements.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField member to encode
   */
  private static void appendEncodeBitField(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    PrimitiveType primitiveType = primitiveTypeForBitFieldSize(resolvedBitField.size());
    appendPrimitiveEncode(
        builder, "this." + resolvedBitField.name(), primitiveType, resolvedBitField.endian());
  }

  /**
   * Resolves the primitive wire type used by one bitField storage size.
   *
   * @param bitFieldSize bitField storage size
   * @return primitive wire type used to encode/decode bitField storage
   */
  private static PrimitiveType primitiveTypeForBitFieldSize(BitFieldSize bitFieldSize) {
    return switch (bitFieldSize) {
      case U8 -> PrimitiveType.UINT8;
      case U16 -> PrimitiveType.UINT16;
      case U32 -> PrimitiveType.UINT32;
      case U64 -> PrimitiveType.UINT64;
    };
  }

  /**
   * Appends float encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the float logical value
   * @param resolvedFloat float definition
   * @param fieldName field/member name for exception messages
   */
  private static void appendEncodeFloat(
      StringBuilder builder,
      String valueExpression,
      ResolvedFloat resolvedFloat,
      String fieldName) {
    String order = byteOrderExpression(resolvedFloat.endian());
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      switch (resolvedFloat.size()) {
        case F16 -> builder
            .append("    writeUInt16(out, Short.toUnsignedInt(Float.floatToFloat16((float) ")
            .append(valueExpression)
            .append(")), ")
            .append(order)
            .append(");\n");
        case F32 -> builder
            .append("    writeInt32(out, Float.floatToIntBits((float) ")
            .append(valueExpression)
            .append("), ")
            .append(order)
            .append(");\n");
        case F64 -> builder
            .append("    writeInt64(out, Double.doubleToLongBits(")
            .append(valueExpression)
            .append("), ")
            .append(order)
            .append(");\n");
      }
      return;
    }

    switch (resolvedFloat.size()) {
      case F16 -> builder
          .append("    writeInt16(out, (short) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -32768L, 32767L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case F32 -> builder
          .append("    writeInt32(out, (int) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -2147483648L, 2147483647L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case F64 -> builder
          .append("    writeInt64(out, scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", Long.MIN_VALUE, Long.MAX_VALUE, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends scaled-int encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the logical scaled value
   * @param resolvedScaledInt scaled-int definition
   * @param fieldName field/member name for exception messages
   */
  private static void appendEncodeScaledInt(
      StringBuilder builder,
      String valueExpression,
      ResolvedScaledInt resolvedScaledInt,
      String fieldName) {
    String order = byteOrderExpression(resolvedScaledInt.endian());
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());

    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("    writeInt8(out, (byte) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -128L, 127L, \"")
          .append(fieldName)
          .append("\"));\n");
      case UINT8 -> builder
          .append("    writeUInt8(out, (short) scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 255L, \"")
          .append(fieldName)
          .append("\"));\n");
      case INT16 -> builder
          .append("    writeInt16(out, (short) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -32768L, 32767L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT16 -> builder
          .append("    writeUInt16(out, (int) scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 65535L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    writeInt32(out, (int) scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", -2147483648L, 2147483647L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    writeUInt32(out, scaleToUnsignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", 4294967295L, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    writeInt64(out, scaleToSignedRaw(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", Long.MIN_VALUE, Long.MAX_VALUE, \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    writeUInt64(out, scaleToUnsignedRaw64(")
          .append(valueExpression)
          .append(", ")
          .append(scaleLiteral)
          .append(", \"")
          .append(fieldName)
          .append("\"), ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends fixed-array encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the array value
   * @param resolvedArray array definition
   * @param fieldName field/member name for exception text
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedArray resolvedArray,
      String fieldName,
      GenerationContext generationContext) {
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedArray.length())
        .append(" elements.\");\n")
        .append("    }\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        builder,
        loopItemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        generationContext);

    builder.append("    }\n");
  }

  /**
   * Appends vector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the vector value
   * @param resolvedVector vector definition
   * @param fieldName field/member name for exception text
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);

    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(valueExpression)
        .append(".length; ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext))
        .append(' ')
        .append(loopItemName)
        .append(" = ")
        .append(valueExpression)
        .append('[')
        .append(loopIndexName)
        .append("];\n");

    appendEncodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder.append("    }\n");

    appendTerminatorEncode(
        builder,
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);
  }

  /**
   * Appends count-field validation for vector/blobVector encoders.
   *
   * @param builder destination source builder
   * @param lengthMode vector/blob length mode
   * @param valueExpression expression that resolves to vector/blob value
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
   */
  private static void appendVectorCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String valueExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Count";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Appends terminator write statements for vector encoders.
   *
   * @param builder destination source builder
   * @param lengthMode vector length mode
   * @param elementTypeRef vector element type
   * @param endian optional vector endian override
   * @param generationContext reusable lookup maps
   */
  private static void appendTerminatorEncode(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    String terminatorLiteral = terminatorLiteral(lengthMode);
    if (terminatorLiteral == null) {
      return;
    }

    BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
    PrimitiveType primitiveType = ((PrimitiveTypeRef) elementTypeRef).primitiveType();
    String terminatorExpression = primitiveLiteralExpression(primitiveType, numericLiteral);

    appendEncodeCollectionElement(
        builder, terminatorExpression, elementTypeRef, endian, generationContext);
  }

  /**
   * Appends one collection element encode statement.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to one element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   * @param generationContext reusable lookup maps
   */
  private static void appendEncodeCollectionElement(
      StringBuilder builder,
      String valueExpression,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveEncode(builder, valueExpression, primitiveTypeRef.primitiveType(), endian);
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      appendMessageEncode(builder, valueExpression, valueExpression, javaType);
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendEncodeFloat(builder, valueExpression, resolvedFloat, valueExpression);
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        generationContext
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    appendEncodeScaledInt(builder, valueExpression, resolvedScaledInt, valueExpression);
  }

  /**
   * Appends blobArray encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobArray blobArray definition
   * @param fieldName field/member name for exception text
   */
  private static void appendEncodeBlobArray(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobArray resolvedBlobArray,
      String fieldName) {
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    if (")
        .append(valueExpression)
        .append(".length != ")
        .append(resolvedBlobArray.length())
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" must contain exactly ")
        .append(resolvedBlobArray.length())
        .append(" bytes.\");\n")
        .append("    }\n")
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(resolvedBlobArray.length())
        .append(");\n");
  }

  /**
   * Appends blobVector encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the blob value
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for exception text
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeBlobVector(
      StringBuilder builder,
      String valueExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n");

    appendVectorCountValidation(
        builder,
        resolvedBlobVector.lengthMode(),
        valueExpression,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    out.write(")
        .append(valueExpression)
        .append(", 0, ")
        .append(valueExpression)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedBlobVector.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends varString encode statements.
   *
   * @param builder destination source builder
   * @param valueExpression expression that resolves to the string value
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for exception text
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendEncodeVarString(
      StringBuilder builder,
      String valueExpression,
      ResolvedVarString resolvedVarString,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String encodedLocalName = "encoded" + toPascalCase(fieldName);
    builder
        .append("    Objects.requireNonNull(")
        .append(valueExpression)
        .append(", \"")
        .append(fieldName)
        .append("\");\n")
        .append("    byte[] ")
        .append(encodedLocalName)
        .append(" = ")
        .append(valueExpression)
        .append(".getBytes(")
        .append(charsetExpression(resolvedVarString.encoding()))
        .append(");\n");

    appendVarStringCountValidation(
        builder,
        resolvedVarString.lengthMode(),
        encodedLocalName,
        primitiveFieldByName,
        ownerPrefix,
        fieldName);

    builder
        .append("    out.write(")
        .append(encodedLocalName)
        .append(", 0, ")
        .append(encodedLocalName)
        .append(".length);\n");

    String terminatorLiteral = terminatorLiteral(resolvedVarString.lengthMode());
    if (terminatorLiteral != null) {
      BigInteger numericLiteral = parseNumericLiteral(terminatorLiteral);
      builder.append("    writeUInt8(out, (short) ").append(numericLiteral).append(");\n");
    }
  }

  /**
   * Appends count-field validation for varString encoders.
   *
   * @param builder destination source builder
   * @param lengthMode varString length mode
   * @param encodedBytesExpression expression that resolves to encoded bytes
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param fieldName field/member name for exception text
   */
  private static void appendVarStringCountValidation(
      StringBuilder builder,
      ResolvedLengthMode lengthMode,
      String encodedBytesExpression,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      String fieldName) {
    if (!(lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength)) {
      return;
    }

    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String localName = "expected" + toPascalCase(fieldName) + "Length";

    builder
        .append("    int ")
        .append(localName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    if (")
        .append(encodedBytesExpression)
        .append(".length != ")
        .append(localName)
        .append(") {\n")
        .append("      throw new IllegalArgumentException(\"")
        .append(fieldName)
        .append(" byte length must match count field ")
        .append(resolvedCountFieldLength.ref())
        .append(".\");\n")
        .append("    }\n");
  }

  /**
   * Appends pad encode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  private static void appendEncodePad(StringBuilder builder, ResolvedPad resolvedPad) {
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
  private static void appendEncodeChecksum(
      StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRange checksumRange = requiredChecksumRange(resolvedChecksum.range());
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
   * @param builder destination source builder
   * @param resolvedIfBlock if-block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendEncodeIfBlock(
      StringBuilder builder,
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    String conditionExpression = ifConditionExpression(resolvedIfBlock.condition(), "this.");
    builder.append("    if (").append(conditionExpression).append(") {\n");
    appendEncodeMembers(
        builder, resolvedIfBlock.members(), messageType, generationContext, primitiveFieldByName);
    builder.append("    }\n");
  }

  /**
   * Resolves optional terminator literal from a length mode.
   *
   * @param lengthMode vector/blob length mode
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedLengthMode lengthMode) {
    if (lengthMode instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      return resolvedTerminatorValueLength.value();
    }
    if (lengthMode instanceof ResolvedTerminatorField resolvedTerminatorField) {
      return terminatorLiteral(resolvedTerminatorField.next());
    }
    return null;
  }

  /**
   * Resolves optional terminator literal from one terminator node.
   *
   * @param terminatorNode terminator node to inspect
   * @return terminator literal when present, otherwise {@code null}
   */
  private static String terminatorLiteral(ResolvedTerminatorNode terminatorNode) {
    if (terminatorNode == null) {
      return null;
    }
    if (terminatorNode instanceof ResolvedTerminatorMatch resolvedTerminatorMatch) {
      return resolvedTerminatorMatch.value();
    }
    return terminatorLiteral(((ResolvedTerminatorField) terminatorNode).next());
  }

  /**
   * Appends decode methods.
   *
   * @param builder destination source builder
   * @param messageType message being rendered
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeMethods(
      StringBuilder builder, ResolvedMessageType messageType, GenerationContext generationContext) {
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
        .append("    Objects.requireNonNull(input, \"input\");\n");
    if (containsChecksumMember(messageType.members())) {
      builder.append("    int messageStartPosition = input.position();\n");
    }
    builder
        .append("    ")
        .append(messageType.name())
        .append(" value = new ")
        .append(messageType.name())
        .append("();\n");

    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    appendDecodeMembers(
        builder, messageType.members(), messageType, generationContext, primitiveFieldByName);

    builder.append("    return value;\n");
    builder.append("  }\n\n");
  }

  /**
   * Appends decode statements for one member list recursively.
   *
   * @param builder destination source builder
   * @param members members to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeMembers(
      StringBuilder builder,
      List<ResolvedMessageMember> members,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    for (ResolvedMessageMember member : members) {
      appendDecodeMember(builder, member, messageType, generationContext, primitiveFieldByName);
    }
  }

  /**
   * Appends decode statements for one member.
   *
   * @param builder destination source builder
   * @param member member to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeMember(
      StringBuilder builder,
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    if (member instanceof ResolvedField resolvedField) {
      appendDecodeField(
          builder, resolvedField, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      appendDecodeBitField(builder, resolvedBitField);
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      appendDecodeFloat(builder, "value." + resolvedFloat.name(), resolvedFloat);
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      appendDecodeScaledInt(builder, "value." + resolvedScaledInt.name(), resolvedScaledInt);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      appendDecodeArray(
          builder,
          "value." + resolvedArray.name(),
          resolvedArray,
          resolvedArray.name(),
          generationContext);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      appendDecodeVector(
          builder,
          "value." + resolvedVector.name(),
          resolvedVector,
          resolvedVector.name(),
          generationContext,
          primitiveFieldByName,
          "value.");
      return;
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      appendDecodeBlobArray(builder, "value." + resolvedBlobArray.name(), resolvedBlobArray);
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      appendDecodeBlobVector(
          builder,
          "value." + resolvedBlobVector.name(),
          resolvedBlobVector,
          resolvedBlobVector.name(),
          primitiveFieldByName,
          "value.");
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      appendDecodeVarString(
          builder,
          "value." + resolvedVarString.name(),
          resolvedVarString,
          resolvedVarString.name(),
          primitiveFieldByName,
          "value.");
      return;
    }
    if (member instanceof ResolvedPad resolvedPad) {
      appendDecodePad(builder, resolvedPad);
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      appendDecodeChecksum(builder, resolvedChecksum);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      appendDecodeIfBlock(
          builder, resolvedIfBlock, messageType, generationContext, primitiveFieldByName);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      appendDecodeMembers(
          builder,
          resolvedNestedType.members(),
          messageType,
          generationContext,
          primitiveFieldByName);
      return;
    }

    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }

  /**
   * Appends decode statements for one field member.
   *
   * @param builder destination source builder
   * @param resolvedField field member to decode
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeField(
      StringBuilder builder,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    String targetExpression = "value." + resolvedField.name();

    if (typeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      appendPrimitiveDecode(
          builder, targetExpression, primitiveTypeRef.primitiveType(), resolvedField.endian());
      return;
    }
    if (typeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      builder
          .append("    ")
          .append(targetExpression)
          .append(" = ")
          .append(javaType)
          .append(".decode(input);\n");
      return;
    }
    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      appendDecodeFloat(builder, targetExpression, resolvedFloat);
      return;
    }
    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      appendDecodeScaledInt(builder, targetExpression, resolvedScaledInt);
      return;
    }
    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      appendDecodeArray(
          builder, targetExpression, resolvedArray, resolvedField.name(), generationContext);
      return;
    }
    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      appendDecodeVector(
          builder,
          targetExpression,
          resolvedVector,
          resolvedField.name(),
          generationContext,
          primitiveFieldByName,
          "value.");
      return;
    }
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      ResolvedBlobArray resolvedBlobArray =
          generationContext.reusableBlobArrayByName().get(blobArrayTypeRef.blobArrayTypeName());
      appendDecodeBlobArray(builder, targetExpression, resolvedBlobArray);
      return;
    }
    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      appendDecodeBlobVector(
          builder,
          targetExpression,
          resolvedBlobVector,
          resolvedField.name(),
          primitiveFieldByName,
          "value.");
      return;
    }
    if (typeRef instanceof VarStringTypeRef varStringTypeRef) {
      ResolvedVarString resolvedVarString =
          generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
      appendDecodeVarString(
          builder,
          targetExpression,
          resolvedVarString,
          resolvedField.name(),
          primitiveFieldByName,
          "value.");
      return;
    }

    throw new IllegalStateException(
        "Unsupported type reference: " + typeRef.getClass().getSimpleName());
  }

  /**
   * Appends primitive decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param primitiveType primitive type to decode
   * @param endian optional endian override
   */
  private static void appendPrimitiveDecode(
      StringBuilder builder, String targetExpression, PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    switch (primitiveType) {
      case UINT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt8(input);\n");
      case UINT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt16(input, ")
          .append(order)
          .append(");\n");
      case UINT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt32(input, ")
          .append(order)
          .append(");\n");
      case UINT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt64(input, ")
          .append(order)
          .append(");\n");
      case INT8 -> builder.append("    ").append(targetExpression).append(" = readInt8(input);\n");
      case INT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(");\n");
      case INT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(");\n");
      case INT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(");\n");
    }
  }

  /**
   * Appends bitField decode statements.
   *
   * @param builder destination source builder
   * @param resolvedBitField bitField member to decode
   */
  private static void appendDecodeBitField(
      StringBuilder builder, ResolvedBitField resolvedBitField) {
    PrimitiveType primitiveType = primitiveTypeForBitFieldSize(resolvedBitField.size());
    appendPrimitiveDecode(
        builder, "value." + resolvedBitField.name(), primitiveType, resolvedBitField.endian());
  }

  /**
   * Appends float decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedFloat float definition
   */
  private static void appendDecodeFloat(
      StringBuilder builder, String targetExpression, ResolvedFloat resolvedFloat) {
    String order = byteOrderExpression(resolvedFloat.endian());
    String scaleLiteral = decimalLiteral(resolvedFloat.scale());

    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      switch (resolvedFloat.size()) {
        case F16 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Float.float16ToFloat((short) readUInt16(input, ")
            .append(order)
            .append("));\n");
        case F32 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Float.intBitsToFloat(readInt32(input, ")
            .append(order)
            .append("));\n");
        case F64 -> builder
            .append("    ")
            .append(targetExpression)
            .append(" = Double.longBitsToDouble(readInt64(input, ")
            .append(order)
            .append("));\n");
      }
      return;
    }

    switch (resolvedFloat.size()) {
      case F16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case F32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case F64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
    }
  }

  /**
   * Appends scaled-int decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedScaledInt scaled-int definition
   */
  private static void appendDecodeScaledInt(
      StringBuilder builder, String targetExpression, ResolvedScaledInt resolvedScaledInt) {
    String order = byteOrderExpression(resolvedScaledInt.endian());
    String scaleLiteral = decimalLiteral(resolvedScaledInt.scale());

    switch (resolvedScaledInt.baseType()) {
      case INT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt8(input) * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT8 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt8(input) * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT16 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt16(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT32 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readUInt32(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case INT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = readInt64(input, ")
          .append(order)
          .append(") * ")
          .append(scaleLiteral)
          .append(";\n");
      case UINT64 -> builder
          .append("    ")
          .append(targetExpression)
          .append(" = unsignedLongToDouble(readUInt64(input, ")
          .append(order)
          .append(")) * ")
          .append(scaleLiteral)
          .append(";\n");
    }
  }

  /**
   * Appends fixed-array decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedArray array definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeArray(
      StringBuilder builder,
      String targetExpression,
      ResolvedArray resolvedArray,
      String fieldName,
      GenerationContext generationContext) {
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);
    String elementType =
        javaElementTypeForCollection(resolvedArray.elementTypeRef(), generationContext);

    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(resolvedArray.length())
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(resolvedArray.length())
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedArray.elementTypeRef(),
        resolvedArray.endian(),
        generationContext);

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends vector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (resolvedVector.lengthMode() instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      appendDecodeCountedVector(
          builder,
          targetExpression,
          resolvedVector,
          fieldName,
          generationContext,
          primitiveFieldByName,
          ownerPrefix,
          resolvedCountFieldLength);
      return;
    }

    appendDecodeTerminatedVector(
        builder,
        targetExpression,
        resolvedVector,
        fieldName,
        generationContext,
        terminatorLiteral(resolvedVector.lengthMode()));
  }

  /**
   * Appends decode statements for count-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   * @param resolvedCountFieldLength count-field length mode
   */
  private static void appendDecodeCountedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix,
      ResolvedCountFieldLength resolvedCountFieldLength) {
    String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
    PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
    String countExpression =
        countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
    String countMethod =
        countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext);
    String loopIndexName = toLoopIndexName(fieldName);
    String loopItemName = toLoopItemName(fieldName);

    builder
        .append("    int ")
        .append(countLocalName)
        .append(" = ")
        .append(countMethod)
        .append('(')
        .append(countExpression)
        .append(", \"")
        .append(resolvedCountFieldLength.ref())
        .append("\");\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(countLocalName)
        .append("];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(countLocalName)
        .append("; ")
        .append(loopIndexName)
        .append("++) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(loopItemName)
        .append(";\n")
        .append("    }\n");
  }

  /**
   * Appends decode statements for terminator-based vectors.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVector vector definition
   * @param fieldName field/member name for local variables
   * @param generationContext reusable lookup maps
   * @param literal terminator literal text
   */
  private static void appendDecodeTerminatedVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedVector resolvedVector,
      String fieldName,
      GenerationContext generationContext,
      String literal) {
    PrimitiveType primitiveType =
        ((PrimitiveTypeRef) resolvedVector.elementTypeRef()).primitiveType();
    BigInteger numericLiteral = parseNumericLiteral(literal);

    String listName = toLoopItemName(fieldName) + "List";
    String loopItemName = toLoopItemName(fieldName);
    String loopIndexName = toLoopIndexName(fieldName);
    String wrapperType = wrapperTypeForPrimitive(primitiveType);
    String elementType =
        javaElementTypeForCollection(resolvedVector.elementTypeRef(), generationContext);

    builder
        .append("    ArrayList<")
        .append(wrapperType)
        .append("> ")
        .append(listName)
        .append(" = new ArrayList<>();\n")
        .append("    while (true) {\n");

    appendDecodeCollectionElement(
        builder,
        loopItemName,
        resolvedVector.elementTypeRef(),
        resolvedVector.endian(),
        generationContext);

    builder
        .append("      if (")
        .append(terminatorComparisonExpression(loopItemName, primitiveType, numericLiteral))
        .append(") {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(listName)
        .append(".add(")
        .append(loopItemName)
        .append(");\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = new ")
        .append(elementType)
        .append('[')
        .append(listName)
        .append(".size()];\n")
        .append("    for (int ")
        .append(loopIndexName)
        .append(" = 0; ")
        .append(loopIndexName)
        .append(" < ")
        .append(listName)
        .append(".size(); ")
        .append(loopIndexName)
        .append("++) {\n")
        .append("      ")
        .append(targetExpression)
        .append('[')
        .append(loopIndexName)
        .append("] = ")
        .append(listName)
        .append(".get(")
        .append(loopIndexName)
        .append(");\n")
        .append("    }\n");
  }

  /**
   * Appends one collection element decode statement.
   *
   * @param builder destination source builder
   * @param targetLocalName local variable name that receives decoded element value
   * @param elementTypeRef collection element type reference
   * @param endian optional endian override from the collection definition
   * @param generationContext reusable lookup maps
   */
  private static void appendDecodeCollectionElement(
      StringBuilder builder,
      String targetLocalName,
      ResolvedTypeRef elementTypeRef,
      Endian endian,
      GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef) {
      builder
          .append("      ")
          .append(primitiveTypeRef.primitiveType().javaTypeName())
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(primitiveDecodeExpression(primitiveTypeRef.primitiveType(), endian))
          .append(";\n");
      return;
    }

    if (elementTypeRef instanceof MessageTypeRef messageTypeRef) {
      String javaType = javaTypeForTypeRef(messageTypeRef, generationContext);
      builder
          .append("      ")
          .append(javaType)
          .append(' ')
          .append(targetLocalName)
          .append(" = ")
          .append(javaType)
          .append(".decode(input);\n");
      return;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      builder.append("      double ").append(targetLocalName).append(";\n");
      appendDecodeFloat(builder, targetLocalName, resolvedFloat);
      return;
    }

    ResolvedScaledInt resolvedScaledInt =
        generationContext
            .reusableScaledIntByName()
            .get(((ScaledIntTypeRef) elementTypeRef).scaledIntTypeName());
    builder.append("      double ").append(targetLocalName).append(";\n");
    appendDecodeScaledInt(builder, targetLocalName, resolvedScaledInt);
  }

  /**
   * Appends blobArray decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobArray blobArray definition
   */
  private static void appendDecodeBlobArray(
      StringBuilder builder, String targetExpression, ResolvedBlobArray resolvedBlobArray) {
    builder
        .append("    ")
        .append(targetExpression)
        .append(" = new byte[")
        .append(resolvedBlobArray.length())
        .append("];\n")
        .append("    input.get(")
        .append(targetExpression)
        .append(");\n");
  }

  /**
   * Appends blobVector decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedBlobVector blobVector definition
   * @param fieldName field/member name for local variables
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeBlobVector(
      StringBuilder builder,
      String targetExpression,
      ResolvedBlobVector resolvedBlobVector,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    if (resolvedBlobVector.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Count";
      PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(targetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedBlobVector.lengthMode()));
    String tempName = toLoopItemName(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toByteArray();\n");
  }

  /**
   * Appends varString decode statements.
   *
   * @param builder destination source builder
   * @param targetExpression assignment target expression
   * @param resolvedVarString varString definition
   * @param fieldName field/member name for local variables
   * @param primitiveFieldByName primitive field lookup map
   * @param ownerPrefix owner expression prefix used for count-field access
   */
  private static void appendDecodeVarString(
      StringBuilder builder,
      String targetExpression,
      ResolvedVarString resolvedVarString,
      String fieldName,
      Map<String, PrimitiveType> primitiveFieldByName,
      String ownerPrefix) {
    String charsetExpression = charsetExpression(resolvedVarString.encoding());
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      String countLocalName = "expected" + toPascalCase(fieldName) + "Length";
      String bytesLocalName = "bytes" + toPascalCase(fieldName);
      PrimitiveType countFieldType = primitiveFieldByName.get(resolvedCountFieldLength.ref());
      String countExpression =
          countExpression(ownerPrefix + resolvedCountFieldLength.ref(), countFieldType);
      String countMethod =
          countFieldType == PrimitiveType.UINT64 ? "requireCountUnsignedLong" : "requireCount";

      builder
          .append("    int ")
          .append(countLocalName)
          .append(" = ")
          .append(countMethod)
          .append('(')
          .append(countExpression)
          .append(", \"")
          .append(resolvedCountFieldLength.ref())
          .append("\");\n")
          .append("    byte[] ")
          .append(bytesLocalName)
          .append(" = new byte[")
          .append(countLocalName)
          .append("];\n")
          .append("    input.get(")
          .append(bytesLocalName)
          .append(");\n")
          .append("    ")
          .append(targetExpression)
          .append(" = new String(")
          .append(bytesLocalName)
          .append(", ")
          .append(charsetExpression)
          .append(");\n");
      return;
    }

    BigInteger numericLiteral =
        parseNumericLiteral(terminatorLiteral(resolvedVarString.lengthMode()));
    String tempName = "bytes" + toPascalCase(fieldName) + "Buffer";
    builder
        .append("    ByteArrayOutputStream ")
        .append(tempName)
        .append(" = new ByteArrayOutputStream();\n")
        .append("    while (true) {\n")
        .append("      short nextByte = readUInt8(input);\n")
        .append("      if ((nextByte & 0xFFL) == ")
        .append(numericLiteral)
        .append("L) {\n")
        .append("        break;\n")
        .append("      }\n")
        .append("      ")
        .append(tempName)
        .append(".write(nextByte & 0xFF);\n")
        .append("    }\n")
        .append("    ")
        .append(targetExpression)
        .append(" = ")
        .append(tempName)
        .append(".toString(")
        .append(charsetExpression)
        .append(");\n");
  }

  /**
   * Appends pad decode statements.
   *
   * @param builder destination source builder
   * @param resolvedPad pad definition
   */
  private static void appendDecodePad(StringBuilder builder, ResolvedPad resolvedPad) {
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
  private static void appendDecodeChecksum(
      StringBuilder builder, ResolvedChecksum resolvedChecksum) {
    ChecksumRange checksumRange = requiredChecksumRange(resolvedChecksum.range());
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
   * @param builder destination source builder
   * @param resolvedIfBlock if-block definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   */
  private static void appendDecodeIfBlock(
      StringBuilder builder,
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName) {
    String conditionExpression = ifConditionExpression(resolvedIfBlock.condition(), "value.");
    builder.append("    if (").append(conditionExpression).append(") {\n");
    appendDecodeMembers(
        builder, resolvedIfBlock.members(), messageType, generationContext, primitiveFieldByName);
    builder.append("    }\n");
  }

  /**
   * Converts one primitive decode operation into a Java expression.
   *
   * @param primitiveType primitive type to decode
   * @param endian optional endian override
   * @return Java expression that decodes one primitive value from input
   */
  private static String primitiveDecodeExpression(PrimitiveType primitiveType, Endian endian) {
    String order = byteOrderExpression(endian);
    return switch (primitiveType) {
      case UINT8 -> "readUInt8(input)";
      case UINT16 -> "readUInt16(input, " + order + ")";
      case UINT32 -> "readUInt32(input, " + order + ")";
      case UINT64 -> "readUInt64(input, " + order + ")";
      case INT8 -> "readInt8(input)";
      case INT16 -> "readInt16(input, " + order + ")";
      case INT32 -> "readInt32(input, " + order + ")";
      case INT64 -> "readInt64(input, " + order + ")";
    };
  }

  /**
   * Builds a primitive count expression from one field expression.
   *
   * @param fieldExpression expression that resolves to the primitive count field
   * @param primitiveType primitive type of the count field
   * @return expression converted to a long-friendly count value
   */
  private static String countExpression(String fieldExpression, PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8 -> "(" + fieldExpression + " & 0xFFL)";
      case UINT16 -> "(" + fieldExpression + " & 0xFFFFL)";
      case UINT32 -> "(" + fieldExpression + " & 0xFFFFFFFFL)";
      case UINT64 -> fieldExpression;
      case INT8, INT16, INT32, INT64 -> fieldExpression;
    };
  }

  /**
   * Builds a local loop-item variable name.
   *
   * @param fieldName field/member name
   * @return deterministic local variable name
   */
  private static String toLoopItemName(String fieldName) {
    return "item" + toPascalCase(fieldName);
  }

  /**
   * Builds a local loop-index variable name.
   *
   * @param fieldName field/member name
   * @return deterministic index variable name
   */
  private static String toLoopIndexName(String fieldName) {
    return "index" + toPascalCase(fieldName);
  }

  /**
   * Returns Java wrapper type for one primitive type.
   *
   * @param primitiveType primitive type to inspect
   * @return wrapper type name used in temporary lists
   */
  private static String wrapperTypeForPrimitive(PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8, INT16 -> "Short";
      case UINT16, INT32 -> "Integer";
      case UINT32, UINT64, INT64 -> "Long";
      case INT8 -> "Byte";
    };
  }

  /**
   * Builds a primitive-typed terminator literal expression.
   *
   * @param primitiveType primitive type of the terminator element
   * @param numericLiteral parsed numeric literal value
   * @return Java expression that yields a value in the primitive type
   */
  private static String primitiveLiteralExpression(
      PrimitiveType primitiveType, BigInteger numericLiteral) {
    return switch (primitiveType) {
      case UINT8 -> "(short) " + numericLiteral;
      case UINT16 -> "(int) " + numericLiteral;
      case UINT32, UINT64 -> numericLiteral.longValue() + "L";
      case INT8 -> "(byte) " + numericLiteral;
      case INT16 -> "(short) " + numericLiteral;
      case INT32 -> "(int) " + numericLiteral;
      case INT64 -> numericLiteral.longValue() + "L";
    };
  }

  /**
   * Builds a terminator equality expression for one primitive local variable.
   *
   * @param valueExpression local variable expression
   * @param primitiveType primitive type of that variable
   * @param numericLiteral parsed terminator literal value
   * @return Java expression that evaluates to true when terminator is reached
   */
  private static String terminatorComparisonExpression(
      String valueExpression, PrimitiveType primitiveType, BigInteger numericLiteral) {
    return switch (primitiveType) {
      case UINT8 -> "((" + valueExpression + " & 0xFFL) == " + numericLiteral + "L)";
      case UINT16 -> "((" + valueExpression + " & 0xFFFFL) == " + numericLiteral + "L)";
      case UINT32 -> "((" + valueExpression + " & 0xFFFFFFFFL) == " + numericLiteral + "L)";
      case UINT64 -> "(Long.compareUnsigned("
          + valueExpression
          + ", "
          + numericLiteral.longValue()
          + "L) == 0)";
      case INT8, INT16, INT32, INT64 -> "(((long) "
          + valueExpression
          + ") == "
          + numericLiteral
          + "L)";
    };
  }

  /**
   * Parses one checksum range string in the form {@code start..end}.
   *
   * @param rangeText checksum range text from XML
   * @return parsed checksum range, or {@code null} when invalid
   */
  private static ChecksumRange parseChecksumRange(String rangeText) {
    int separator = rangeText.indexOf("..");
    if (separator < 0 || separator != rangeText.lastIndexOf("..")) {
      return null;
    }

    String startText = rangeText.substring(0, separator).trim();
    String endText = rangeText.substring(separator + 2).trim();
    if (startText.isEmpty() || endText.isEmpty()) {
      return null;
    }

    try {
      BigInteger start = parseNumericLiteral(startText);
      BigInteger end = parseNumericLiteral(endText);
      if (start.compareTo(BigInteger.ZERO) < 0 || end.compareTo(BigInteger.ZERO) < 0) {
        return null;
      }
      if (start.compareTo(end) > 0) {
        return null;
      }
      if (start.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
          || end.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
        return null;
      }
      return new ChecksumRange(start.intValueExact(), end.intValueExact());
    } catch (NumberFormatException | ArithmeticException exception) {
      return null;
    }
  }

  /**
   * Resolves and renders one if-condition expression for generated Java code.
   *
   * @param condition resolved condition tree
   * @param ownerPrefix expression prefix for the owning value ({@code this.} or {@code value.})
   * @return Java boolean expression that evaluates the condition
   */
  private static String ifConditionExpression(ResolvedIfCondition condition, String ownerPrefix) {
    if (condition instanceof ResolvedIfComparison comparison) {
      return comparisonExpression(comparison, ownerPrefix);
    }
    if (condition instanceof ResolvedIfAndCondition andCondition) {
      return "("
          + ifConditionExpression(andCondition.left(), ownerPrefix)
          + " && "
          + ifConditionExpression(andCondition.right(), ownerPrefix)
          + ")";
    }
    ResolvedIfOrCondition orCondition = (ResolvedIfOrCondition) condition;
    return "("
        + ifConditionExpression(orCondition.left(), ownerPrefix)
        + " || "
        + ifConditionExpression(orCondition.right(), ownerPrefix)
        + ")";
  }

  /**
   * Renders one primitive comparison node into Java code.
   *
   * @param comparison resolved comparison node
   * @param ownerPrefix expression prefix for the owning value ({@code this.} or {@code value.})
   * @return Java boolean expression for the comparison
   */
  private static String comparisonExpression(ResolvedIfComparison comparison, String ownerPrefix) {
    String fieldExpression = ownerPrefix + comparison.fieldName();
    if (comparison.fieldType() == PrimitiveType.UINT64) {
      return "(Long.compareUnsigned("
          + fieldExpression
          + ", Long.parseUnsignedLong(\""
          + comparison.literal()
          + "\")) "
          + unsignedLongComparisonSuffix(comparison.operator())
          + ")";
    }
    return "("
        + countExpression(fieldExpression, comparison.fieldType())
        + " "
        + signedLongComparisonOperator(comparison.operator())
        + " "
        + comparison.literal()
        + "L)";
  }

  /**
   * Converts a parsed operator to the Java operator used for signed long comparisons.
   *
   * @param operator comparison operator
   * @return Java operator for signed long comparison
   */
  private static String signedLongComparisonOperator(IfComparisonOperator operator) {
    return switch (operator) {
      case EQ -> "==";
      case NE -> "!=";
      case LT -> "<";
      case LTE -> "<=";
      case GT -> ">";
      case GTE -> ">=";
      default -> throw new IllegalStateException("Unsupported operator: " + operator);
    };
  }

  /**
   * Converts a parsed operator to the suffix used with {@link Long#compareUnsigned(long, long)}.
   *
   * @param operator comparison operator
   * @return Java comparison suffix, for example {@code == 0} or {@code < 0}
   */
  private static String unsignedLongComparisonSuffix(IfComparisonOperator operator) {
    return switch (operator) {
      case EQ -> "== 0";
      case NE -> "!= 0";
      case LT -> "< 0";
      case LTE -> "<= 0";
      case GT -> "> 0";
      case GTE -> ">= 0";
      default -> throw new IllegalStateException("Unsupported operator: " + operator);
    };
  }

  /**
   * Parses a checksum range and fails hard when the value is unexpectedly invalid.
   *
   * @param rangeText checksum range text from XML
   * @return parsed checksum range
   */
  private static ChecksumRange requiredChecksumRange(String rangeText) {
    ChecksumRange checksumRange = parseChecksumRange(rangeText);
    if (checksumRange == null) {
      throw new IllegalStateException("Unsupported checksum range: " + rangeText);
    }
    return checksumRange;
  }

  /**
   * Parses one numeric literal used by terminator modes.
   *
   * @param literal raw literal text from XML
   * @return parsed integer value
   * @throws NumberFormatException when the literal is not numeric
   */
  private static BigInteger parseNumericLiteral(String literal) {
    String trimmed = literal.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return new BigInteger(trimmed.substring(2), 16);
    }
    if (trimmed.startsWith("-") && (trimmed.startsWith("-0x") || trimmed.startsWith("-0X"))) {
      return new BigInteger(trimmed.substring(3), 16).negate();
    }
    if (trimmed.matches("-?[0-9]+")) {
      return new BigInteger(trimmed, 10);
    }
    return new BigInteger(trimmed, 16);
  }

  /**
   * Checks whether a numeric literal fits the value range for one primitive type.
   *
   * @param numericLiteral parsed numeric literal
   * @param primitiveType primitive target type
   * @return {@code true} when the literal is representable by the primitive type
   */
  private static boolean fitsPrimitiveRange(
      BigInteger numericLiteral, PrimitiveType primitiveType) {
    return switch (primitiveType) {
      case UINT8 -> inRange(numericLiteral, BigInteger.ZERO, BigInteger.valueOf(255));
      case UINT16 -> inRange(numericLiteral, BigInteger.ZERO, BigInteger.valueOf(65_535));
      case UINT32 -> inRange(numericLiteral, BigInteger.ZERO, BigInteger.valueOf(4_294_967_295L));
      case UINT64 -> inRange(
          numericLiteral, BigInteger.ZERO, new BigInteger("18446744073709551615"));
      case INT8 -> inRange(numericLiteral, BigInteger.valueOf(-128), BigInteger.valueOf(127));
      case INT16 -> inRange(
          numericLiteral, BigInteger.valueOf(-32_768), BigInteger.valueOf(32_767));
      case INT32 -> inRange(
          numericLiteral,
          BigInteger.valueOf(Integer.MIN_VALUE),
          BigInteger.valueOf(Integer.MAX_VALUE));
      case INT64 -> inRange(
          numericLiteral, BigInteger.valueOf(Long.MIN_VALUE), BigInteger.valueOf(Long.MAX_VALUE));
    };
  }

  /**
   * Checks whether a numeric value is between inclusive lower and upper bounds.
   *
   * @param value value to check
   * @param lowerInclusive lower bound (inclusive)
   * @param upperInclusive upper bound (inclusive)
   * @return {@code true} when value is in range
   */
  private static boolean inRange(
      BigInteger value, BigInteger lowerInclusive, BigInteger upperInclusive) {
    return value.compareTo(lowerInclusive) >= 0 && value.compareTo(upperInclusive) <= 0;
  }

  /**
   * Converts an optional endian value to a Java {@link java.nio.ByteOrder} expression.
   *
   * @param endian optional endian override from the resolved model
   * @return Java source expression for the matching byte order
   */
  private static String byteOrderExpression(Endian endian) {
    if (endian == Endian.LITTLE) {
      return "ByteOrder.LITTLE_ENDIAN";
    }
    return "ByteOrder.BIG_ENDIAN";
  }

  /**
   * Converts one resolved string encoding to a Java {@link java.nio.charset.Charset} expression.
   *
   * @param encoding resolved string encoding
   * @return Java source expression for the matching charset
   */
  private static String charsetExpression(StringEncoding encoding) {
    if (encoding == StringEncoding.ASCII) {
      return "StandardCharsets.US_ASCII";
    }
    return "StandardCharsets.UTF_8";
  }

  /**
   * Converts an optional decimal value to a deterministic Java double literal.
   *
   * @param decimal decimal value from the resolved model
   * @return Java double literal string
   */
  private static String decimalLiteral(BigDecimal decimal) {
    BigDecimal safeDecimal = decimal == null ? BigDecimal.ZERO : decimal;
    return safeDecimal.toPlainString() + "d";
  }

  /**
   * Parsed checksum range bounds.
   *
   * @param startInclusive first byte index (inclusive)
   * @param endInclusive last byte index (inclusive)
   */
  private record ChecksumRange(int startInclusive, int endInclusive) {}

  /** Immutable lookup maps used while rendering one schema. */
  private record GenerationContext(
      Map<String, ResolvedMessageType> messageTypeByName,
      Map<String, ResolvedFloat> reusableFloatByName,
      Map<String, ResolvedScaledInt> reusableScaledIntByName,
      Map<String, ResolvedArray> reusableArrayByName,
      Map<String, ResolvedVector> reusableVectorByName,
      Map<String, ResolvedBlobArray> reusableBlobArrayByName,
      Map<String, ResolvedBlobVector> reusableBlobVectorByName,
      Map<String, ResolvedVarString> reusableVarStringByName) {}
}
