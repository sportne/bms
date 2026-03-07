package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import java.math.BigDecimal;
import java.util.Objects;

/** Parsed representation of a {@code scaledInt} member or reusable scaled-int type. */
public record ParsedScaledInt(
    String name, String baseTypeName, BigDecimal scale, Endian endian, String comment)
    implements ParsedMessageMember {

  public ParsedScaledInt {
    name = Objects.requireNonNull(name, "name");
    baseTypeName = Objects.requireNonNull(baseTypeName, "baseTypeName");
    scale = Objects.requireNonNull(scale, "scale");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
