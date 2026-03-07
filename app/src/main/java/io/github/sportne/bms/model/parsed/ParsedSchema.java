package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of the whole XML {@code <schema>} document.
 *
 * <p>This model keeps reusable type definitions and message definitions before semantic
 * normalization.
 */
public record ParsedSchema(
    String namespace,
    List<ParsedMessageType> messageTypes,
    List<ParsedBitField> reusableBitFields,
    List<ParsedFloat> reusableFloats,
    List<ParsedScaledInt> reusableScaledInts) {

  public ParsedSchema {
    namespace = Objects.requireNonNull(namespace, "namespace");
    messageTypes = List.copyOf(Objects.requireNonNull(messageTypes, "messageTypes"));
    reusableBitFields = List.copyOf(Objects.requireNonNull(reusableBitFields, "reusableBitFields"));
    reusableFloats = List.copyOf(Objects.requireNonNull(reusableFloats, "reusableFloats"));
    reusableScaledInts =
        List.copyOf(Objects.requireNonNull(reusableScaledInts, "reusableScaledInts"));
  }

  /**
   * Convenience constructor for older tests and call sites that only supply message types.
   *
   * <p>Reusable type lists default to empty.
   */
  public ParsedSchema(String namespace, List<ParsedMessageType> messageTypes) {
    this(namespace, messageTypes, List.of(), List.of(), List.of());
  }
}
