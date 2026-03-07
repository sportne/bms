package io.github.sportne.bms.model;

/** Supported ways a floating-point value can be encoded on the wire. */
public enum FloatEncoding {
  IEEE754("ieee754"),
  SCALED("scaled");

  private final String xmlValue;

  FloatEncoding(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  /** Returns the XML literal for this encoding. */
  public String xmlValue() {
    return xmlValue;
  }

  /** Converts an XML literal such as {@code ieee754} into a {@link FloatEncoding}. */
  public static FloatEncoding fromXml(String value) {
    return switch (value) {
      case "ieee754" -> IEEE754;
      case "scaled" -> SCALED;
      default -> throw new IllegalArgumentException("Unsupported float encoding: " + value);
    };
  }
}
