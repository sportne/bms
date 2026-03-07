package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parsed representation of a {@code float} member or reusable float type.
 *
 * @param name float name
 * @param size float storage size
 * @param encoding float encoding mode
 * @param scale optional scale value (required by scaled encoding)
 * @param endian optional byte order override
 * @param comment human-readable description
 */
public record ParsedFloat(
    String name,
    FloatSize size,
    FloatEncoding encoding,
    BigDecimal scale,
    Endian endian,
    String comment)
    implements ParsedMessageMember {
  /**
   * Creates a parsed float member.
   *
   * @param name float name
   * @param size storage size
   * @param encoding encoding mode
   * @param scale optional scale value
   * @param endian optional byte order override
   * @param comment human-readable description
   */
  public ParsedFloat {
    name = Objects.requireNonNull(name, "name");
    size = Objects.requireNonNull(size, "size");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
