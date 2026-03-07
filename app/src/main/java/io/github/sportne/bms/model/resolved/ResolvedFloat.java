package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import java.math.BigDecimal;
import java.util.Objects;

/** Resolved representation of a float member after semantic checks. */
public record ResolvedFloat(
    String name,
    FloatSize size,
    FloatEncoding encoding,
    BigDecimal scale,
    Endian endian,
    String comment)
    implements ResolvedMessageMember {

  public ResolvedFloat {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
