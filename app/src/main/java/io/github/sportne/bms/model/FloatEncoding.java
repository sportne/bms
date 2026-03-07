package io.github.sportne.bms.model;

/** Supported ways a floating-point value can be encoded on the wire. */
public enum FloatEncoding {
  IEEE754("ieee754"),
  SCALED("scaled");

  private final String xmlValue;

  /**
   * Creates one enum entry.
   *
   * @param xmlValue XML literal for this encoding
   */
  FloatEncoding(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  /** Returns the XML literal for this encoding. */
  public String xmlValue() {
    return xmlValue;
  }

  /**
   * Converts an XML literal such as {@code ieee754} into a {@link FloatEncoding}.
   *
   * @param value XML literal from the schema
   * @return enum value matching that literal
   */
  public static FloatEncoding fromXml(String value) {
    return switch (value) {
      case "ieee754" -> IEEE754;
      case "scaled" -> SCALED;
      default -> throw new IllegalArgumentException("Unsupported float encoding: " + value);
    };
  }
}
