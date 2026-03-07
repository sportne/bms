package io.github.sportne.bms.model;

/**
 * Allowed storage sizes for a {@code bitField} element.
 *
 * <p>Each value stores both:
 *
 * <ul>
 *   <li>the exact XML literal (for example {@code u16})
 *   <li>the number of bits represented by that literal
 * </ul>
 */
public enum BitFieldSize {
  U8("u8", 8),
  U16("u16", 16),
  U32("u32", 32),
  U64("u64", 64);

  private final String xmlValue;
  private final int bitWidth;

  /**
   * Creates one enum entry.
   *
   * @param xmlValue XML literal for this size
   * @param bitWidth number of bits represented by this size
   */
  BitFieldSize(String xmlValue, int bitWidth) {
    this.xmlValue = xmlValue;
    this.bitWidth = bitWidth;
  }

  /** Returns the number of bits available in this bit-field size. */
  public int bitWidth() {
    return bitWidth;
  }

  /**
   * Converts an XML literal such as {@code u8} into a {@link BitFieldSize}.
   *
   * @param value XML literal from the schema
   * @return enum value matching that literal
   */
  public static BitFieldSize fromXml(String value) {
    return switch (value) {
      case "u8" -> U8;
      case "u16" -> U16;
      case "u32" -> U32;
      case "u64" -> U64;
      default -> throw new IllegalArgumentException("Unsupported bit field size: " + value);
    };
  }

  /** Returns the exact string used in XML for this size. */
  public String xmlValue() {
    return xmlValue;
  }
}
