package io.github.sportne.bms.codegen.common;

import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable lookup index for schema-level resolved definitions.
 *
 * <p>Both generators use these maps to resolve reusable type references in deterministic order.
 */
public final class SchemaIndex {
  private final Map<String, ResolvedMessageType> messageTypeByName;
  private final Map<String, ResolvedFloat> reusableFloatByName;
  private final Map<String, ResolvedScaledInt> reusableScaledIntByName;
  private final Map<String, ResolvedArray> reusableArrayByName;
  private final Map<String, ResolvedVector> reusableVectorByName;
  private final Map<String, ResolvedBlobArray> reusableBlobArrayByName;
  private final Map<String, ResolvedBlobVector> reusableBlobVectorByName;
  private final Map<String, ResolvedVarString> reusableVarStringByName;

  /**
   * Creates an immutable schema index.
   *
   * @param messageTypeByName message lookup keyed by message name
   * @param reusableFloatByName float lookup keyed by reusable type name
   * @param reusableScaledIntByName scaled-int lookup keyed by reusable type name
   * @param reusableArrayByName array lookup keyed by reusable type name
   * @param reusableVectorByName vector lookup keyed by reusable type name
   * @param reusableBlobArrayByName blob-array lookup keyed by reusable type name
   * @param reusableBlobVectorByName blob-vector lookup keyed by reusable type name
   * @param reusableVarStringByName varString lookup keyed by reusable type name
   */
  private SchemaIndex(
      Map<String, ResolvedMessageType> messageTypeByName,
      Map<String, ResolvedFloat> reusableFloatByName,
      Map<String, ResolvedScaledInt> reusableScaledIntByName,
      Map<String, ResolvedArray> reusableArrayByName,
      Map<String, ResolvedVector> reusableVectorByName,
      Map<String, ResolvedBlobArray> reusableBlobArrayByName,
      Map<String, ResolvedBlobVector> reusableBlobVectorByName,
      Map<String, ResolvedVarString> reusableVarStringByName) {
    this.messageTypeByName = Map.copyOf(messageTypeByName);
    this.reusableFloatByName = Map.copyOf(reusableFloatByName);
    this.reusableScaledIntByName = Map.copyOf(reusableScaledIntByName);
    this.reusableArrayByName = Map.copyOf(reusableArrayByName);
    this.reusableVectorByName = Map.copyOf(reusableVectorByName);
    this.reusableBlobArrayByName = Map.copyOf(reusableBlobArrayByName);
    this.reusableBlobVectorByName = Map.copyOf(reusableBlobVectorByName);
    this.reusableVarStringByName = Map.copyOf(reusableVarStringByName);
  }

  /**
   * Builds one index from a resolved schema.
   *
   * @param schema resolved schema that owns message and reusable definitions
   * @return immutable index used by backend generators
   */
  public static SchemaIndex fromResolvedSchema(ResolvedSchema schema) {
    return new SchemaIndex(
        indexByName(schema.messageTypes(), ResolvedMessageType::name),
        indexByName(schema.reusableFloats(), ResolvedFloat::name),
        indexByName(schema.reusableScaledInts(), ResolvedScaledInt::name),
        indexByName(schema.reusableArrays(), ResolvedArray::name),
        indexByName(schema.reusableVectors(), ResolvedVector::name),
        indexByName(schema.reusableBlobArrays(), ResolvedBlobArray::name),
        indexByName(schema.reusableBlobVectors(), ResolvedBlobVector::name),
        indexByName(schema.reusableVarStrings(), ResolvedVarString::name));
  }

  /**
   * Returns message lookup keyed by message type name.
   *
   * @return immutable message lookup map
   */
  public Map<String, ResolvedMessageType> messageTypeByName() {
    return messageTypeByName;
  }

  /**
   * Returns reusable float lookup keyed by reusable type name.
   *
   * @return immutable reusable-float lookup map
   */
  public Map<String, ResolvedFloat> reusableFloatByName() {
    return reusableFloatByName;
  }

  /**
   * Returns reusable scaled-int lookup keyed by reusable type name.
   *
   * @return immutable reusable-scaled-int lookup map
   */
  public Map<String, ResolvedScaledInt> reusableScaledIntByName() {
    return reusableScaledIntByName;
  }

  /**
   * Returns reusable array lookup keyed by reusable type name.
   *
   * @return immutable reusable-array lookup map
   */
  public Map<String, ResolvedArray> reusableArrayByName() {
    return reusableArrayByName;
  }

  /**
   * Returns reusable vector lookup keyed by reusable type name.
   *
   * @return immutable reusable-vector lookup map
   */
  public Map<String, ResolvedVector> reusableVectorByName() {
    return reusableVectorByName;
  }

  /**
   * Returns reusable blob-array lookup keyed by reusable type name.
   *
   * @return immutable reusable-blob-array lookup map
   */
  public Map<String, ResolvedBlobArray> reusableBlobArrayByName() {
    return reusableBlobArrayByName;
  }

  /**
   * Returns reusable blob-vector lookup keyed by reusable type name.
   *
   * @return immutable reusable-blob-vector lookup map
   */
  public Map<String, ResolvedBlobVector> reusableBlobVectorByName() {
    return reusableBlobVectorByName;
  }

  /**
   * Returns reusable varString lookup keyed by reusable type name.
   *
   * @return immutable reusable-varString lookup map
   */
  public Map<String, ResolvedVarString> reusableVarStringByName() {
    return reusableVarStringByName;
  }

  /**
   * Builds a deterministic index map from one list of named definitions.
   *
   * @param definitions named definitions to index
   * @param nameExtractor function that returns each definition name
   * @param <T> definition type
   * @return immutable map keyed by extracted name
   */
  private static <T> Map<String, T> indexByName(
      List<T> definitions, Function<T, String> nameExtractor) {
    Map<String, T> indexedValues = new LinkedHashMap<>();
    for (T definition : definitions) {
      indexedValues.put(nameExtractor.apply(definition), definition);
    }
    return Map.copyOf(indexedValues);
  }
}
