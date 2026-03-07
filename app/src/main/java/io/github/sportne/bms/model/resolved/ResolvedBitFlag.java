package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/** Resolved representation of one flag bit. */
public record ResolvedBitFlag(String name, int position, String comment) {

  public ResolvedBitFlag {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
  }
}
