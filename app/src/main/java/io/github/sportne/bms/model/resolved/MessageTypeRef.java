package io.github.sportne.bms.model.resolved;

import java.util.Objects;

public record MessageTypeRef(String messageTypeName) implements ResolvedTypeRef {
  public MessageTypeRef {
    messageTypeName = Objects.requireNonNull(messageTypeName, "messageTypeName");
  }
}
