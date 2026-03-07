package io.github.sportne.bms.model;

/** Supported bit widths for floating-point values. */
public enum FloatSize {
  F16("f16"),
  F32("f32"),
  F64("f64");

  private final String xmlValue;

  FloatSize(String xmlValue) {
    this.xmlValue = xmlValue;
  }

  /** Returns the XML literal for this size. */
  public String xmlValue() {
    return xmlValue;
  }

  /** Converts an XML literal such as {@code f32} into a {@link FloatSize}. */
  public static FloatSize fromXml(String value) {
    return switch (value) {
      case "f16" -> F16;
      case "f32" -> F32;
      case "f64" -> F64;
      default -> throw new IllegalArgumentException("Unsupported float size: " + value);
    };
  }
}
