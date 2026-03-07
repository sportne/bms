package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Resolved representation of explicit byte padding.
 *
 * @param bytes number of bytes to reserve
 * @param comment optional human-readable description
 */
public record ResolvedPad(int bytes, String comment) implements ResolvedMessageMember {

  /**
   * Creates a resolved pad definition.
   *
   * @param bytes number of bytes to reserve
   * @param comment optional human-readable description
   */
  public ResolvedPad {
    comment = Objects.requireNonNullElse(comment, "");
  }
}
