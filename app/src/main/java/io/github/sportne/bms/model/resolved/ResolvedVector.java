package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Resolved representation of a variable-length {@code vector}.
 *
 * @param name vector name
 * @param elementTypeRef resolved element type
 * @param endian optional byte order override
 * @param comment human-readable description
 * @param lengthMode resolved length/termination strategy
 */
public record ResolvedVector(
    String name,
    ResolvedTypeRef elementTypeRef,
    Endian endian,
    String comment,
    ResolvedLengthMode lengthMode)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved vector definition.
   *
   * @param name vector name
   * @param elementTypeRef resolved element type
   * @param endian optional byte order override
   * @param comment human-readable description
   * @param lengthMode resolved length/termination strategy
   */
  public ResolvedVector {
    name = Objects.requireNonNull(name, "name");
    elementTypeRef = Objects.requireNonNull(elementTypeRef, "elementTypeRef");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
