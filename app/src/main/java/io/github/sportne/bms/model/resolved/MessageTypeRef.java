package io.github.sportne.bms.model.resolved;

import java.util.Objects;

/**
 * Type reference to another resolved {@code messageType}.
 *
 * @param messageTypeName target message type name
 */
public record MessageTypeRef(String messageTypeName) implements ResolvedTypeRef {
  /**
   * Creates a reference to another message type.
   *
   * @param messageTypeName target message type name
   */
  public MessageTypeRef {
    messageTypeName = Objects.requireNonNull(messageTypeName, "messageTypeName");
  }
}
