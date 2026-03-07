package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import java.math.BigDecimal;
import java.util.Objects;

/** Parsed representation of a {@code float} member or reusable float type. */
public record ParsedFloat(
    String name,
    FloatSize size,
    FloatEncoding encoding,
    BigDecimal scale,
    Endian endian,
    String comment)
    implements ParsedMessageMember {

  public ParsedFloat {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
