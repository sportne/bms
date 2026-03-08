package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import java.util.List;

/**
 * Registers schema-level type definitions for lookup and duplicate-name checks.
 *
 * <p>This class performs the "registry build" phase before reusable/message resolution begins.
 */
final class TypeRegistryBuilder {
  /** Prevents instantiation of this static helper class. */
  private TypeRegistryBuilder() {}

  /**
   * Registers all schema-level named type definitions.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param context shared semantic-resolution state
   */
  static void registerSchemaLevelTypes(ParsedSchema parsedSchema, ResolutionContext context) {
    registerMessageTypes(parsedSchema.messageTypes(), context);
    registerReusableFloats(parsedSchema.reusableFloats(), context);
    registerReusableScaledInts(parsedSchema.reusableScaledInts(), context);
    registerReusableArrays(parsedSchema.reusableArrays(), context);
    registerReusableVectors(parsedSchema.reusableVectors(), context);
    registerReusableBlobArrays(parsedSchema.reusableBlobArrays(), context);
    registerReusableBlobVectors(parsedSchema.reusableBlobVectors(), context);
    registerReusableVarStrings(parsedSchema.reusableVarStrings(), context);
  }

  /**
   * Registers message-type names and validates message metadata.
   *
   * @param messageTypes parsed message definitions
   * @param context shared semantic-resolution state
   */
  private static void registerMessageTypes(
      List<ParsedMessageType> messageTypes, ResolutionContext context) {
    for (ParsedMessageType messageType : messageTypes) {
      String sourcePath = context.sourcePathFor(messageType);
      SemanticValidationRules.validateIdentifier(
          messageType.name(),
          "SEMANTIC_INVALID_MESSAGE_NAME",
          "Message type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      ParsedMessageType previous =
          context.messageTypeByName.putIfAbsent(messageType.name(), messageType);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_MESSAGE_TYPE",
                duplicateMessage(
                    "message type", messageType.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
      registerGlobalTypeName(messageType.name(), messageType, context);
      if (messageType.namespaceOverride() != null) {
        SemanticValidationRules.validateNamespace(
            messageType.namespaceOverride(),
            "messageType@namespace",
            sourcePath,
            context.diagnostics);
      }
    }
  }

  /**
   * Registers reusable float definitions and validates names.
   *
   * @param reusableFloats parsed reusable float definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableFloats(
      List<ParsedFloat> reusableFloats, ResolutionContext context) {
    for (ParsedFloat parsedFloat : reusableFloats) {
      String sourcePath = context.sourcePathFor(parsedFloat);
      SemanticValidationRules.validateIdentifier(
          parsedFloat.name(),
          "SEMANTIC_INVALID_FLOAT_NAME",
          "Float type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedFloat.name(), parsedFloat, context);
      ParsedFloat previous =
          context.reusableFloatByName.putIfAbsent(parsedFloat.name(), parsedFloat);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_FLOAT_TYPE",
                duplicateMessage(
                    "reusable float", parsedFloat.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable scaled-int definitions and validates names.
   *
   * @param reusableScaledInts parsed reusable scaled-int definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableScaledInts(
      List<ParsedScaledInt> reusableScaledInts, ResolutionContext context) {
    for (ParsedScaledInt parsedScaledInt : reusableScaledInts) {
      String sourcePath = context.sourcePathFor(parsedScaledInt);
      SemanticValidationRules.validateIdentifier(
          parsedScaledInt.name(),
          "SEMANTIC_INVALID_SCALED_INT_NAME",
          "ScaledInt type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedScaledInt.name(), parsedScaledInt, context);
      ParsedScaledInt previous =
          context.reusableScaledIntByName.putIfAbsent(parsedScaledInt.name(), parsedScaledInt);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_SCALED_INT_TYPE",
                duplicateMessage(
                    "reusable scaledInt", parsedScaledInt.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable array definitions and validates names.
   *
   * @param reusableArrays parsed reusable array definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableArrays(
      List<ParsedArray> reusableArrays, ResolutionContext context) {
    for (ParsedArray parsedArray : reusableArrays) {
      String sourcePath = context.sourcePathFor(parsedArray);
      SemanticValidationRules.validateIdentifier(
          parsedArray.name(),
          "SEMANTIC_INVALID_ARRAY_NAME",
          "Array type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedArray.name(), parsedArray, context);
      ParsedArray previous =
          context.reusableArrayByName.putIfAbsent(parsedArray.name(), parsedArray);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_ARRAY_TYPE",
                duplicateMessage(
                    "reusable array", parsedArray.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable vector definitions and validates names.
   *
   * @param reusableVectors parsed reusable vector definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableVectors(
      List<ParsedVector> reusableVectors, ResolutionContext context) {
    for (ParsedVector parsedVector : reusableVectors) {
      String sourcePath = context.sourcePathFor(parsedVector);
      SemanticValidationRules.validateIdentifier(
          parsedVector.name(),
          "SEMANTIC_INVALID_VECTOR_NAME",
          "Vector type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVector.name(), parsedVector, context);
      ParsedVector previous =
          context.reusableVectorByName.putIfAbsent(parsedVector.name(), parsedVector);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_VECTOR_TYPE",
                duplicateMessage(
                    "reusable vector", parsedVector.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable blob-array definitions and validates names.
   *
   * @param reusableBlobArrays parsed reusable blob-array definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableBlobArrays(
      List<ParsedBlobArray> reusableBlobArrays, ResolutionContext context) {
    for (ParsedBlobArray parsedBlobArray : reusableBlobArrays) {
      String sourcePath = context.sourcePathFor(parsedBlobArray);
      SemanticValidationRules.validateIdentifier(
          parsedBlobArray.name(),
          "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
          "blobArray type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobArray.name(), parsedBlobArray, context);
      ParsedBlobArray previous =
          context.reusableBlobArrayByName.putIfAbsent(parsedBlobArray.name(), parsedBlobArray);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE",
                duplicateMessage(
                    "reusable blobArray", parsedBlobArray.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable blob-vector definitions and validates names.
   *
   * @param reusableBlobVectors parsed reusable blob-vector definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableBlobVectors(
      List<ParsedBlobVector> reusableBlobVectors, ResolutionContext context) {
    for (ParsedBlobVector parsedBlobVector : reusableBlobVectors) {
      String sourcePath = context.sourcePathFor(parsedBlobVector);
      SemanticValidationRules.validateIdentifier(
          parsedBlobVector.name(),
          "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
          "blobVector type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobVector.name(), parsedBlobVector, context);
      ParsedBlobVector previous =
          context.reusableBlobVectorByName.putIfAbsent(parsedBlobVector.name(), parsedBlobVector);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE",
                duplicateMessage(
                    "reusable blobVector",
                    parsedBlobVector.name(),
                    context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers reusable varString definitions and validates names.
   *
   * @param reusableVarStrings parsed reusable varString definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableVarStrings(
      List<ParsedVarString> reusableVarStrings, ResolutionContext context) {
    for (ParsedVarString parsedVarString : reusableVarStrings) {
      String sourcePath = context.sourcePathFor(parsedVarString);
      SemanticValidationRules.validateIdentifier(
          parsedVarString.name(),
          "SEMANTIC_INVALID_VAR_STRING_NAME",
          "varString type name must be a valid identifier: ",
          sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVarString.name(), parsedVarString, context);
      ParsedVarString previous =
          context.reusableVarStringByName.putIfAbsent(parsedVarString.name(), parsedVarString);
      if (previous != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_VAR_STRING_TYPE",
                duplicateMessage(
                    "reusable varString", parsedVarString.name(), context.sourcePathFor(previous)),
                sourcePath));
      }
    }
  }

  /**
   * Registers one type name in the schema-level global type set.
   *
   * @param typeName type name being registered
   * @param definition owner parsed definition
   * @param context shared semantic-resolution state
   */
  private static void registerGlobalTypeName(
      String typeName, Object definition, ResolutionContext context) {
    String sourcePath = context.sourcePathFor(definition);
    context.firstSourcePathByGlobalTypeName.putIfAbsent(typeName, sourcePath);
    if (!context.globalTypeNames.add(typeName)) {
      String firstSourcePath = context.firstSourcePathByGlobalTypeName.get(typeName);
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_TYPE_NAME",
              duplicateMessage("global type", typeName, firstSourcePath),
              sourcePath));
    }
  }

  /**
   * Builds one duplicate-definition message that includes first-definition provenance.
   *
   * @param definitionKind human-readable definition kind label
   * @param name duplicate name
   * @param firstSourcePath source path where the name was first observed
   * @return human-readable duplicate-definition message
   */
  private static String duplicateMessage(
      String definitionKind, String name, String firstSourcePath) {
    return "Duplicate "
        + definitionKind
        + " name: "
        + name
        + ". First defined in: "
        + firstSourcePath;
  }
}
