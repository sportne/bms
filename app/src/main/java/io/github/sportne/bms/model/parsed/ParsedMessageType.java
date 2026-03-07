package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

public record ParsedMessageType(
    String name, String comment, String namespaceOverride, List<ParsedField> fields) {

  public ParsedMessageType {
    name = Objects.requireNonNull(name, "name");
    comment = Objects.requireNonNull(comment, "comment");
    fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
  }
}
