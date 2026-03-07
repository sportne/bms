package io.github.sportne.bms.model.resolved;

import io.github.sportne.bms.model.StringEncoding;
import java.util.Objects;

/**
 * Resolved representation of a variable-length string member.
 *
 * @param name string name
 * @param encoding resolved text encoding
 * @param comment human-readable description
 * @param lengthMode resolved length/termination strategy
 */
public record ResolvedVarString(
    String name, StringEncoding encoding, String comment, ResolvedLengthMode lengthMode)
    implements ResolvedMessageMember {

  /**
   * Creates a resolved variable-length string member.
   *
   * @param name string name
   * @param encoding resolved text encoding
   * @param comment human-readable description
   * @param lengthMode resolved length/termination strategy
   */
  public ResolvedVarString {
    name = Objects.requireNonNull(name, "name");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
