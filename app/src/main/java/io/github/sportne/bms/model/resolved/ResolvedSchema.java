package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of an entire BMS schema.
 *
 * <p>Generators should read only this layer, not the parsed XML layer.
 */
public record ResolvedSchema(
    String namespace,
    List<ResolvedMessageType> messageTypes,
    List<ResolvedBitField> reusableBitFields,
    List<ResolvedFloat> reusableFloats,
    List<ResolvedScaledInt> reusableScaledInts) {

  public ResolvedSchema {
    namespace = Objects.requireNonNull(namespace, "namespace");
    messageTypes = List.copyOf(Objects.requireNonNull(messageTypes, "messageTypes"));
    reusableBitFields = List.copyOf(Objects.requireNonNull(reusableBitFields, "reusableBitFields"));
    reusableFloats = List.copyOf(Objects.requireNonNull(reusableFloats, "reusableFloats"));
    reusableScaledInts =
        List.copyOf(Objects.requireNonNull(reusableScaledInts, "reusableScaledInts"));
  }

  /**
   * Convenience constructor for call sites that only provide resolved messages.
   *
   * <p>Reusable type lists default to empty.
   */
  public ResolvedSchema(String namespace, List<ResolvedMessageType> messageTypes) {
    this(namespace, messageTypes, List.of(), List.of(), List.of());
  }
}
