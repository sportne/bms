package io.github.sportne.bms.model;

/**
 * Supported string encoding values used by {@code varString}.
 *
 * <p>The XML values are lowercase so specs stay short and predictable.
 */
public enum StringEncoding {
  /** 7-bit ASCII text. */
  ASCII("ascii"),
  /** UTF-8 text. */
  UTF8("utf8");

  private final String xmlValue;

  /**
   * Creates one string-encoding enum value.
   *
   * @param xmlValue lowercase value used in XML attributes
   */
  StringEncoding(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  /**
   * Returns the lowercase value written in XML files.
   *
   * @return XML encoding literal
   */
  public String xmlValue() {
    return xmlValue;
  }

  /**
   * Converts an XML literal into the matching enum value.
   *
   * @param value XML encoding literal
   * @return matching encoding enum
   * @throws IllegalArgumentException when no encoding matches the input
   */
  public static StringEncoding fromXml(String value) {
    for (StringEncoding encoding : values()) {
      if (encoding.xmlValue.equals(value)) {
        return encoding;
      }
    }
    throw new IllegalArgumentException("Unsupported string encoding: " + value);
  }
}
