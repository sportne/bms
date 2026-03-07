package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a named {@code bitField} block.
 *
 * <p>This object is close to XML and is validated further in the semantic phase.
 *
 * @param name bitField name from XML
 * @param size storage size for the bitfield container
 * @param endian optional byte order override
 * @param comment human-readable description
 * @param flags named single-bit entries in declaration order
 * @param segments named multi-bit entries in declaration order
 */
public record ParsedBitField(
    String name,
    BitFieldSize size,
    Endian endian,
    String comment,
    List<ParsedBitFlag> flags,
    List<ParsedBitSegment> segments)
    implements ParsedMessageMember {
  /**
   * Creates a parsed bitfield object.
   *
   * @param name bitfield name
   * @param size storage size
   * @param endian optional byte order override
   * @param comment human-readable description
   * @param flags named single-bit entries
   * @param segments named multi-bit entries
   */
  public ParsedBitField {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    comment = Objects.requireNonNull(comment, "comment");
    flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
    segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
  }
}
