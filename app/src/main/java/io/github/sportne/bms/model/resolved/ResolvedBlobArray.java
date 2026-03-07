package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Resolved representation of a fixed-length {@code blobArray}.
 *
 * @param name blob name
 * @param length fixed byte length
 * @param comment human-readable description
 */
public record ResolvedBlobArray(String name, int length, String comment)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved blob-array definition.
   *
   * @param name blob name
   * @param length fixed byte length
   * @param comment human-readable description
   */
  public ResolvedBlobArray {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
