package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/** Type reference to another resolved {@code messageType}. */
public record MessageTypeRef(String messageTypeName) implements ResolvedTypeRef {
  public MessageTypeRef {
    messageTypeName = Objects.requireNonNull(messageTypeName, "messageTypeName");
  }
}
