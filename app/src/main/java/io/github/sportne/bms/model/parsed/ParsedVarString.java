package io.github.sportne.bms.model.parsed;

import io.github.sportne.bms.model.StringEncoding;
import java.util.Objects;

/**
 * Parsed representation of a variable-length string member.
 *
 * @param name string name
 * @param encoding text encoding declared in XML
 * @param comment human-readable description
 * @param lengthMode string length/termination strategy
 */
public record ParsedVarString(
    String name, StringEncoding encoding, String comment, ParsedLengthMode lengthMode)
    implements ParsedMessageMember {

  /**
   * Creates a parsed variable-length string definition.
   *
   * @param name string name
   * @param encoding text encoding declared in XML
   * @param comment human-readable description
   * @param lengthMode string length/termination strategy
   */
  public ParsedVarString {
    name = Objects.requireNonNull(name, "name");
    encoding = Objects.requireNonNull(encoding, "encoding");
    comment = Objects.requireNonNull(comment, "comment");
    lengthMode = Objects.requireNonNull(lengthMode, "lengthMode");
  }
}
