package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Parsed representation of a variable-length {@code vector}.
 *
 * @param name vector name
 * @param elementTypeName element type name from XML
 * @param endian optional byte order override
 * @param comment human-readable description
 * @param lengthMode length/termination strategy
 */
public record ParsedVector(
    String name, String elementTypeName, Endian endian, String comment, ParsedLengthMode lengthMode)
    implements ParsedMessageMember {

  /**
   * Creates a parsed vector definition.
   *
   * @param name vector name
   * @param elementTypeName element type name from XML
   * @param endian optional byte order override
   * @param comment human-readable description
   * @param lengthMode length/termination strategy
   */
  public ParsedVector {
    name = Objects.requireNonNull(name, "name");
    elementTypeName = Objects.requireNonNull(elementTypeName, "elementTypeName");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
