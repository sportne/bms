package io.github.sportne.bms.model;

/** Byte order for multi-byte numbers in the wire format. */
public enum Endian {
  LITTLE,
  BIG;

  /**
   * Converts XML text into an {@link Endian} value.
   *
   * <p>Returns {@code null} when the XML attribute is omitted.
   *
   * @param value XML literal such as {@code little} or {@code big}
   * @return parsed endian value, or {@code null} when input is {@code null}
   */
  public static Endian fromXml(String value) {
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "little" -> LITTLE;
      case "big" -> BIG;
      default -> throw new IllegalArgumentException("Unsupported endian value: " + value);
    };
  }
}
