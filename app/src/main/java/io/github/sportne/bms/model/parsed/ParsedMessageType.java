package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of one {@code messageType} definition.
 *
 * <p>This layer keeps data close to XML and postpones deeper checks to semantic resolution.
 *
 * @param name message type name
 * @param comment human-readable description
 * @param namespaceOverride optional namespace override for this message
 * @param members members in exact XML declaration order
 */
public record ParsedMessageType(
    String name, String comment, String namespaceOverride, List<ParsedMessageMember> members) {
  /**
   * Creates a parsed message-type object.
   *
   * @param name message type name
   * @param comment human-readable description
   * @param namespaceOverride optional namespace override
   * @param members members in declaration order
   */
  public ParsedMessageType {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }

  /**
   * Returns only scalar {@code field} members, keeping their original declaration order.
   *
   * <p>This is a convenience helper for code that currently operates on fields only.
   */
  public List<ParsedField> fields() {
    return members.stream()
        .filter(ParsedField.class::isInstance)
        .map(ParsedField.class::cast)
        .toList();
  }
}
