package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parsed representation of a {@code scaledInt} member or reusable scaled-int type.
 *
 * @param name scaled-int name
 * @param baseTypeName primitive base type name
 * @param scale scale factor applied to/from raw integer values
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ParsedScaledInt(
    String name, String baseTypeName, BigDecimal scale, Endian endian, String comment)
    implements ParsedMessageMember {
  /**
   * Creates a parsed scaled-int member.
   *
   * @param name scaled-int name
   * @param baseTypeName primitive base type name
   * @param scale scale factor
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ParsedScaledInt {
    name = Objects.requireNonNull(name, "name");
    baseTypeName = Objects.requireNonNull(baseTypeName, "baseTypeName");
    scale = Objects.requireNonNull(scale, "scale");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
