package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of one message type used by generators.
 *
 * <p>{@code effectiveNamespace} is the final namespace after applying schema defaults and optional
 * per-message overrides.
 */
public record ResolvedMessageType(
    String name, String comment, String effectiveNamespace, List<ResolvedMessageMember> members) {

  public ResolvedMessageType {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    effectiveNamespace = Objects.requireNonNull(effectiveNamespace, "effectiveNamespace");
    members = List.copyOf(Objects.requireNonNull(members, "members"));
  }

  /**
   * Returns only scalar field members, preserving declaration order.
   *
   * <p>This helps generators that are not ready for non-field member kinds yet.
   */
  public List<ResolvedField> fields() {
    return members.stream()
        .filter(ResolvedField.class::isInstance)
        .map(ResolvedField.class::cast)
        .toList();
  }
}
