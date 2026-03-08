package acme.telemetry.conditional.relational;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

public final class ConditionalRelationalFrame {
  public short version;
  public short ltMode;
  public short lteMode;
  public short gtMode;
  public short gteMode;

  public byte[] encode() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeUInt8(out, this.version);
    if (((this.version & 0xFFL) < 10L)) {
    writeUInt8(out, this.ltMode);
    }
    if (((this.version & 0xFFL) <= 10L)) {
    writeUInt8(out, this.lteMode);
    }
    if (((this.version & 0xFFL) > 1L)) {
    writeUInt8(out, this.gtMode);
    }
    if (((this.version & 0xFFL) >= 1L)) {
    writeUInt8(out, this.gteMode);
    }
    return out.toByteArray();
  }

  /**
   * Decodes a message instance from a byte array.
   *
   * @param bytes encoded message bytes
   * @return decoded ConditionalRelationalFrame value
   */
  public static ConditionalRelationalFrame decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return decode(ByteBuffer.wrap(bytes));
  }

  /**
   * Decodes a message instance from a byte buffer.
   *
   * @param input buffer positioned at the start of this message
   * @return decoded ConditionalRelationalFrame value
   */
  public static ConditionalRelationalFrame decode(ByteBuffer input) {
    Objects.requireNonNull(input, "input");
    ConditionalRelationalFrame value = new ConditionalRelationalFrame();
    value.version = readUInt8(input);
    if (((value.version & 0xFFL) < 10L)) {
    value.ltMode = readUInt8(input);
    }
    if (((value.version & 0xFFL) <= 10L)) {
    value.lteMode = readUInt8(input);
    }
    if (((value.version & 0xFFL) > 1L)) {
    value.gtMode = readUInt8(input);
    }
    if (((value.version & 0xFFL) >= 1L)) {
    value.gteMode = readUInt8(input);
    }
    return value;
  }

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
}
