package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Parsed representation of a scalar {@code field} member.
 *
 * @param name field name
 * @param typeName field type name from XML
 * @param length optional fixed length hint
 * @param endian optional byte order override
 * @param fixed optional literal/fixed value
 * @param comment human-readable description
 */
public record ParsedField(
    String name, String typeName, Integer length, Endian endian, String fixed, String comment)
    implements ParsedMessageMember {
  /**
   * Creates a parsed scalar field.
   *
   * @param name field name
   * @param typeName field type name from XML
   * @param length optional length hint
   * @param endian optional byte order override
   * @param fixed optional fixed value literal
   * @param comment human-readable description
   */
  public ParsedField {
    name = Objects.requireNonNull(name, "name");
    typeName = Objects.requireNonNull(typeName, "typeName");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
