package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.util.Objects;

/** Resolved representation of a scalar field member. */
public record ResolvedField(
    String name,
    ResolvedTypeRef typeRef,
    Integer length,
    Endian endian,
    String fixed,
    String comment)
    implements ResolvedMessageMember {

  public ResolvedField {
    name = Objects.requireNonNull(name, "name");
    typeRef = Objects.requireNonNull(typeRef, "typeRef");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
