package io.github.sportne.bms.model.resolved;

import java.util.List;
import java.util.Objects;

/**
 * Resolved representation of an entire BMS schema.
 *
 * <p>Generators should read only this layer, not the parsed XML layer.
 *
 * @param namespace schema-level logical namespace
 * @param messageTypes resolved message type definitions
 * @param reusableBitFields schema-level reusable bitfield definitions
 * @param reusableFloats schema-level reusable float definitions
 * @param reusableScaledInts schema-level reusable scaled-int definitions
 */
public record ResolvedSchema(
    String namespace,
    List<ResolvedMessageType> messageTypes,
    List<ResolvedBitField> reusableBitFields,
    List<ResolvedFloat> reusableFloats,
    List<ResolvedScaledInt> reusableScaledInts) {
  /**
   * Creates a resolved schema object.
   *
   * @param namespace schema-level namespace
   * @param messageTypes resolved message types
   * @param reusableBitFields resolved reusable bitfields
   * @param reusableFloats resolved reusable floats
   * @param reusableScaledInts resolved reusable scaled ints
   */
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
   *
   * @param namespace schema-level logical namespace
   * @param messageTypes resolved message type definitions
   */
  public ResolvedSchema(String namespace, List<ResolvedMessageType> messageTypes) {
    this(namespace, messageTypes, List.of(), List.of(), List.of());
  }
}
