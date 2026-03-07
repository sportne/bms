package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Resolved representation of a checksum declaration.
 *
 * @param algorithm checksum algorithm literal
 * @param range source-byte range expression
 * @param comment human-readable description
 */
public record ResolvedChecksum(String algorithm, String range, String comment)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved checksum definition.
   *
   * @param algorithm checksum algorithm literal
   * @param range source-byte range expression
   * @param comment human-readable description
   */
  public ResolvedChecksum {
    algorithm = Objects.requireNonNull(algorithm, "algorithm");
    range = Objects.requireNonNull(range, "range");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
