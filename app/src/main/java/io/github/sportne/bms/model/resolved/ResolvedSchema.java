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
 * @param reusableArrays schema-level reusable array definitions
 * @param reusableVectors schema-level reusable vector definitions
 * @param reusableBlobArrays schema-level reusable blob-array definitions
 * @param reusableBlobVectors schema-level reusable blob-vector definitions
 */
public record ResolvedSchema(
    String namespace,
    List<ResolvedMessageType> messageTypes,
    List<ResolvedBitField> reusableBitFields,
    List<ResolvedFloat> reusableFloats,
    List<ResolvedScaledInt> reusableScaledInts,
    List<ResolvedArray> reusableArrays,
    List<ResolvedVector> reusableVectors,
    List<ResolvedBlobArray> reusableBlobArrays,
    List<ResolvedBlobVector> reusableBlobVectors) {
  /**
   * Creates a resolved schema object.
   *
   * @param namespace schema-level namespace
   * @param messageTypes resolved message types
   * @param reusableBitFields resolved reusable bitfields
   * @param reusableFloats resolved reusable floats
   * @param reusableScaledInts resolved reusable scaled ints
   * @param reusableArrays resolved reusable arrays
   * @param reusableVectors resolved reusable vectors
   * @param reusableBlobArrays resolved reusable blob arrays
   * @param reusableBlobVectors resolved reusable blob vectors
   */
  public ResolvedSchema {
    namespace = Objects.requireNonNull(namespace, "namespace");
    messageTypes = List.copyOf(Objects.requireNonNull(messageTypes, "messageTypes"));
    reusableBitFields = List.copyOf(Objects.requireNonNull(reusableBitFields, "reusableBitFields"));
    reusableFloats = List.copyOf(Objects.requireNonNull(reusableFloats, "reusableFloats"));
    reusableScaledInts =
        List.copyOf(Objects.requireNonNull(reusableScaledInts, "reusableScaledInts"));
    reusableArrays = List.copyOf(Objects.requireNonNull(reusableArrays, "reusableArrays"));
    reusableVectors = List.copyOf(Objects.requireNonNull(reusableVectors, "reusableVectors"));
    reusableBlobArrays =
        List.copyOf(Objects.requireNonNull(reusableBlobArrays, "reusableBlobArrays"));
    reusableBlobVectors =
        List.copyOf(Objects.requireNonNull(reusableBlobVectors, "reusableBlobVectors"));
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
    this(
        namespace,
        messageTypes,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /**
   * Convenience constructor for call sites that do not pass collection definitions yet.
   *
   * @param namespace schema-level logical namespace
   * @param messageTypes resolved message type definitions
   * @param reusableBitFields schema-level reusable bitfield definitions
   * @param reusableFloats schema-level reusable float definitions
   * @param reusableScaledInts schema-level reusable scaled-int definitions
   */
  public ResolvedSchema(
      String namespace,
      List<ResolvedMessageType> messageTypes,
      List<ResolvedBitField> reusableBitFields,
      List<ResolvedFloat> reusableFloats,
      List<ResolvedScaledInt> reusableScaledInts) {
    this(
        namespace,
        messageTypes,
        reusableBitFields,
        reusableFloats,
        reusableScaledInts,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
