package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Resolved representation of one flag bit.
 *
 * @param name flag name
 * @param position bit position inside the bitfield container
 * @param comment human-readable description
 */
public record ResolvedBitFlag(String name, int position, String comment) {
  /**
   * Creates a resolved bit flag.
   *
   * @param name flag name
   * @param position bit position in the container
   * @param comment human-readable description
   */
  public ResolvedBitFlag {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
