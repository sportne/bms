package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resolved representation of a scaled integer member.
 *
 * @param name scaled-int name
 * @param baseType primitive storage type
 * @param scale scale factor applied to/from raw integer values
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ResolvedScaledInt(
    String name, PrimitiveType baseType, BigDecimal scale, Endian endian, String comment)
    implements ResolvedMessageMember {
  /**
   * Creates a resolved scaled-int member.
   *
   * @param name scaled-int name
   * @param baseType primitive base type
   * @param scale scale factor
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ResolvedScaledInt {
    name = Objects.requireNonNull(name, "name");
    baseType = Objects.requireNonNull(baseType, "baseType");
    scale = Objects.requireNonNull(scale, "scale");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
