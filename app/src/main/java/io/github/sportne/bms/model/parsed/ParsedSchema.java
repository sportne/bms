package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

public record ParsedSchema(String namespace, List<ParsedMessageType> messageTypes) {

  public ParsedSchema {
    namespace = Objects.requireNonNull(namespace, "namespace");
    messageTypes = List.copyOf(Objects.requireNonNull(messageTypes, "messageTypes"));
  }
}
