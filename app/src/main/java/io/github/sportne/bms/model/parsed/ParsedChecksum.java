package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of a checksum declaration.
 *
 * @param algorithm checksum algorithm literal (for example {@code crc32})
 * @param range source-byte range expression
 * @param comment human-readable description
 */
public record ParsedChecksum(String algorithm, String range, String comment)
    implements ParsedMessageMember {

  /**
   * Creates a parsed checksum definition.
   *
   * @param algorithm checksum algorithm literal
   * @param range source-byte range expression
   * @param comment human-readable description
   */
  public ParsedChecksum {
    algorithm = Objects.requireNonNull(algorithm, "algorithm");
    range = Objects.requireNonNull(range, "range");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
