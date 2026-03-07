package io.github.sportne.bms.model.parsed;

import java.util.Objects;

/**
 * Parsed representation of a fixed-length {@code blobArray}.
 *
 * @param name blob name
 * @param length fixed byte length
 * @param comment human-readable description
 */
public record ParsedBlobArray(String name, int length, String comment)
    implements ParsedMessageMember {

  /**
   * Creates a parsed blob-array definition.
   *
   * @param name blob name
   * @param length fixed byte length
   * @param comment human-readable description
   */
  public ParsedBlobArray {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
