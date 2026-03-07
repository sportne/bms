package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import java.util.List;
import java.util.Objects;

/** Resolved representation of a {@code bitField} member. */
public record ResolvedBitField(
    BitFieldSize size,
    Endian endian,
    String comment,
    List<ResolvedBitFlag> flags,
    List<ResolvedBitSegment> segments)
    implements ResolvedMessageMember {

  public ResolvedBitField {
    size = Objects.requireNonNull(size, "size");
    comment = Objects.requireNonNull(comment, "comment");
    flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
    segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
  }
}
