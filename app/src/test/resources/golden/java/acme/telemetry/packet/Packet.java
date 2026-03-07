package acme.telemetry.packet;

import acme.telemetry.Header;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

public final class Packet {
  public Header header;
  public long payloadLength;

  public byte[] encode() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Objects.requireNonNull(this.header, "header");
    byte[] encodedHeader = this.header.encode();
    out.write(encodedHeader, 0, encodedHeader.length);
    writeUInt32(out, this.payloadLength, ByteOrder.BIG_ENDIAN);
    return out.toByteArray();
  }

  /**
   * Decodes a message instance from a byte array.
   *
   * @param bytes encoded message bytes
   * @return decoded Packet value
   */
  public static Packet decode(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    return decode(ByteBuffer.wrap(bytes));
  }

  /**
   * Decodes a message instance from a byte buffer.
   *
   * @param input buffer positioned at the start of this message
   * @return decoded Packet value
   */
  public static Packet decode(ByteBuffer input) {
    Objects.requireNonNull(input, "input");
    Packet value = new Packet();
    value.header = Header.decode(input);
    value.payloadLength = readUInt32(input, ByteOrder.BIG_ENDIAN);
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
}
