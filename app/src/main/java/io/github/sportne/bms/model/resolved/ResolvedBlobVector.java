package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Resolved representation of a variable-length {@code blobVector}.
 *
 * @param name blob vector name
 * @param comment human-readable description
 * @param lengthMode resolved length/termination strategy
 */
public record ResolvedBlobVector(String name, String comment, ResolvedLengthMode lengthMode)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved blob-vector definition.
   *
   * @param name blob vector name
   * @param comment human-readable description
   * @param lengthMode resolved length/termination strategy
   */
  public ResolvedBlobVector {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
