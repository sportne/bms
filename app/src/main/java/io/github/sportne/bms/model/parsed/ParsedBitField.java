package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a {@code bitField} block.
 *
 * <p>This object is close to XML and is validated further in the semantic phase.
 */
public record ParsedBitField(
    BitFieldSize size,
    Endian endian,
    String comment,
    List<ParsedBitFlag> flags,
    List<ParsedBitSegment> segments)
    implements ParsedMessageMember {

  public ParsedBitField {
    size = Objects.requireNonNull(size, "size");
    comment = Objects.requireNonNull(comment, "comment");
    flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
    segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
  }
}
