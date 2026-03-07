package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of a named {@code bitField} member.
 *
 * @param name bitfield name
 * @param size storage size for the bitfield container
 * @param endian optional byte order override
 * @param comment human-readable description
 * @param flags resolved flag entries in declaration order
 * @param segments resolved segment entries in declaration order
 */
public record ResolvedBitField(
    String name,
    BitFieldSize size,
    Endian endian,
    String comment,
    List<ResolvedBitFlag> flags,
    List<ResolvedBitSegment> segments)
    implements ResolvedMessageMember {
  /**
   * Creates a resolved bitfield member.
   *
   * @param name bitfield name
   * @param size storage size
   * @param endian optional byte order override
   * @param comment human-readable description
   * @param flags resolved flag entries
   * @param segments resolved segment entries
   */
  public ResolvedBitField {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    comment = Objects.requireNonNull(comment, "comment");
    flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
    segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
  }
}
