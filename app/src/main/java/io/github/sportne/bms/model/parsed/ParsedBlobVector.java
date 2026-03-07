package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of a variable-length {@code blobVector}.
 *
 * @param name blob vector name
 * @param comment human-readable description
 * @param lengthMode length/termination strategy
 */
public record ParsedBlobVector(String name, String comment, ParsedLengthMode lengthMode)
    implements ParsedMessageMember {

  /**
   * Creates a parsed blob-vector definition.
   *
   * @param name blob vector name
   * @param comment human-readable description
   * @param lengthMode length/termination strategy
   */
  public ParsedBlobVector {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
