package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/**
 * Resolved representation of a scalar field member.
 *
 * @param name field name
 * @param typeRef resolved type reference
 * @param length optional fixed length hint
 * @param endian optional byte order override
 * @param fixed optional literal/fixed value
 * @param comment human-readable description
 */
public record ResolvedField(
    String name,
    ResolvedTypeRef typeRef,
    Integer length,
    Endian endian,
    String fixed,
    String comment)
    implements ResolvedMessageMember {
  /**
   * Creates a resolved scalar field member.
   *
   * @param name field name
   * @param typeRef resolved type reference
   * @param length optional length hint
   * @param endian optional byte order override
   * @param fixed optional fixed value literal
   * @param comment human-readable description
   */
  public ResolvedField {
    name = Objects.requireNonNull(name, "name");
    typeRef = Objects.requireNonNull(typeRef, "typeRef");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
