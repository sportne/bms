package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

public record ResolvedMessageType(
    String name, String comment, String effectiveNamespace, List<ResolvedField> fields) {

  public ResolvedMessageType {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    effectiveNamespace = Objects.requireNonNull(effectiveNamespace, "effectiveNamespace");
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
  }
}
