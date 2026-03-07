package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Parsed representation of an {@code array} member or reusable array type.
 *
 * @param name array name
 * @param elementTypeName element type name from XML
 * @param length fixed element count
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ParsedArray(
    String name, String elementTypeName, int length, Endian endian, String comment)
    implements ParsedMessageMember {

  /**
   * Creates a parsed array definition.
   *
   * @param name array name
   * @param elementTypeName element type name from XML
   * @param length fixed element count
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ParsedArray {
    name = Objects.requireNonNull(name, "name");
    elementTypeName = Objects.requireNonNull(elementTypeName, "elementTypeName");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
