package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

public record ResolvedSchema(String namespace, List<ResolvedMessageType> messageTypes) {

  public ResolvedSchema {
    namespace = Objects.requireNonNull(namespace, "namespace");
    messageTypes = List.copyOf(Objects.requireNonNull(messageTypes, "messageTypes"));
  }
}
