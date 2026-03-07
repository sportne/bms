package io.github.sportne.bms.model.parsed;

import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of the whole XML {@code <schema>} document.
 *
 * <p>This model keeps reusable type definitions and message definitions before semantic
 * normalization.
 *
 * @param namespace schema-level logical namespace
 * @param messageTypes parsed message type definitions
 * @param reusableBitFields schema-level reusable bitfield definitions
 * @param reusableFloats schema-level reusable float definitions
 * @param reusableScaledInts schema-level reusable scaled-int definitions
 * @param reusableArrays schema-level reusable array definitions
 * @param reusableVectors schema-level reusable vector definitions
 * @param reusableBlobArrays schema-level reusable blob-array definitions
 * @param reusableBlobVectors schema-level reusable blob-vector definitions
 * @param reusableVarStrings schema-level reusable varString definitions
 * @param reusableChecksums schema-level checksum definitions
 * @param reusablePads schema-level pad definitions
 */
public record ParsedSchema(
    String namespace,
    List<ParsedMessageType> messageTypes,
    List<ParsedBitField> reusableBitFields,
    List<ParsedFloat> reusableFloats,
    List<ParsedScaledInt> reusableScaledInts,
    List<ParsedArray> reusableArrays,
    List<ParsedVector> reusableVectors,
    List<ParsedBlobArray> reusableBlobArrays,
    List<ParsedBlobVector> reusableBlobVectors,
    List<ParsedVarString> reusableVarStrings,
    List<ParsedChecksum> reusableChecksums,
    List<ParsedPad> reusablePads) {
  /**
   * Creates a parsed schema object.
   *
   * @param namespace schema-level namespace
   * @param messageTypes parsed message types
   * @param reusableBitFields parsed reusable bitfields
   * @param reusableFloats parsed reusable floats
   * @param reusableScaledInts parsed reusable scaled ints
   * @param reusableArrays parsed reusable arrays
   * @param reusableVectors parsed reusable vectors
   * @param reusableBlobArrays parsed reusable blob arrays
   * @param reusableBlobVectors parsed reusable blob vectors
   * @param reusableVarStrings parsed reusable varStrings
   * @param reusableChecksums parsed reusable checksums
   * @param reusablePads parsed reusable pads
   */
  public ParsedSchema {
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
    reusableVarStrings =
        List.copyOf(Objects.requireNonNull(reusableVarStrings, "reusableVarStrings"));
    reusableChecksums = List.copyOf(Objects.requireNonNull(reusableChecksums, "reusableChecksums"));
    reusablePads = List.copyOf(Objects.requireNonNull(reusablePads, "reusablePads"));
  }

  /**
   * Convenience constructor for older tests and call sites that only supply message types.
   *
   * <p>Reusable type lists default to empty.
   *
   * @param namespace schema-level logical namespace
   * @param messageTypes parsed message type definitions
   */
  public ParsedSchema(String namespace, List<ParsedMessageType> messageTypes) {
    this(
        namespace,
        messageTypes,
        List.of(),
        List.of(),
        List.of(),
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
   * @param messageTypes parsed message type definitions
   * @param reusableBitFields schema-level reusable bitfield definitions
   * @param reusableFloats schema-level reusable float definitions
   * @param reusableScaledInts schema-level reusable scaled-int definitions
   */
  public ParsedSchema(
      String namespace,
      List<ParsedMessageType> messageTypes,
      List<ParsedBitField> reusableBitFields,
      List<ParsedFloat> reusableFloats,
      List<ParsedScaledInt> reusableScaledInts) {
    this(
        namespace,
        messageTypes,
        reusableBitFields,
        reusableFloats,
        reusableScaledInts,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  /**
   * Compatibility constructor that keeps the pre-milestone reusable-type parameter list.
   *
   * @param namespace schema-level logical namespace
   * @param messageTypes parsed message type definitions
   * @param reusableBitFields schema-level reusable bitfield definitions
   * @param reusableFloats schema-level reusable float definitions
   * @param reusableScaledInts schema-level reusable scaled-int definitions
   * @param reusableArrays schema-level reusable array definitions
   * @param reusableVectors schema-level reusable vector definitions
   * @param reusableBlobArrays schema-level reusable blob-array definitions
   * @param reusableBlobVectors schema-level reusable blob-vector definitions
   */
  public ParsedSchema(
      String namespace,
      List<ParsedMessageType> messageTypes,
      List<ParsedBitField> reusableBitFields,
      List<ParsedFloat> reusableFloats,
      List<ParsedScaledInt> reusableScaledInts,
      List<ParsedArray> reusableArrays,
      List<ParsedVector> reusableVectors,
      List<ParsedBlobArray> reusableBlobArrays,
      List<ParsedBlobVector> reusableBlobVectors) {
    this(
        namespace,
        messageTypes,
        reusableBitFields,
        reusableFloats,
        reusableScaledInts,
        reusableArrays,
        reusableVectors,
        reusableBlobArrays,
        reusableBlobVectors,
        List.of(),
        List.of(),
        List.of());
  }
}
