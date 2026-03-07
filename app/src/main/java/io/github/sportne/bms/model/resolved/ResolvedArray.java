package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Resolved representation of an {@code array} member or reusable array type.
 *
 * @param name array name
 * @param elementTypeRef resolved element type
 * @param length fixed element count
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ResolvedArray(
    String name, ResolvedTypeRef elementTypeRef, int length, Endian endian, String comment)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved array definition.
   *
   * @param name array name
   * @param elementTypeRef resolved element type
   * @param length fixed element count
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ResolvedArray {
    name = Objects.requireNonNull(name, "name");
    elementTypeRef = Objects.requireNonNull(elementTypeRef, "elementTypeRef");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
