package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.math.BigDecimal;
import java.util.Objects;

/** Resolved representation of a scaled integer member. */
public record ResolvedScaledInt(
    String name, PrimitiveType baseType, BigDecimal scale, Endian endian, String comment)
    implements ResolvedMessageMember {

  public ResolvedScaledInt {
    name = Objects.requireNonNull(name, "name");
    baseType = Objects.requireNonNull(baseType, "baseType");
    scale = Objects.requireNonNull(scale, "scale");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
