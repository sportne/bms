package io.github.sportne.bms.model;

public enum Endian {
  LITTLE,
  BIG;

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
