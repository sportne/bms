package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Resolved representation of a float member after semantic checks.
 *
 * @param name float name
 * @param size float storage size
 * @param encoding float encoding mode
 * @param scale optional scale value (required by scaled encoding)
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ResolvedFloat(
    String name,
    FloatSize size,
    FloatEncoding encoding,
    BigDecimal scale,
    Endian endian,
    String comment)
    implements ResolvedMessageMember {
  /**
   * Creates a resolved float member.
   *
   * @param name float name
   * @param size storage size
   * @param encoding encoding mode
   * @param scale optional scale value
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ResolvedFloat {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
