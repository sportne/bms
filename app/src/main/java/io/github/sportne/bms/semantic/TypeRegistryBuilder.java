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
      SemanticValidationRules.validateIdentifier(
          messageType.name(),
          "SEMANTIC_INVALID_MESSAGE_NAME",
          "Message type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      if (context.messageTypeByName.putIfAbsent(messageType.name(), messageType) != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_MESSAGE_TYPE",
                "Duplicate message type name: " + messageType.name(),
                context.sourcePath));
      }
      registerGlobalTypeName(messageType.name(), context);
      if (messageType.namespaceOverride() != null) {
        SemanticValidationRules.validateNamespace(
            messageType.namespaceOverride(),
            "messageType@namespace",
            context.sourcePath,
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
      SemanticValidationRules.validateIdentifier(
          parsedFloat.name(),
          "SEMANTIC_INVALID_FLOAT_NAME",
          "Float type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedFloat.name(), context);
      if (context.reusableFloatByName.putIfAbsent(parsedFloat.name(), parsedFloat) != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_FLOAT_TYPE",
                "Duplicate reusable float name: " + parsedFloat.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedScaledInt.name(),
          "SEMANTIC_INVALID_SCALED_INT_NAME",
          "ScaledInt type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedScaledInt.name(), context);
      if (context.reusableScaledIntByName.putIfAbsent(parsedScaledInt.name(), parsedScaledInt)
          != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_SCALED_INT_TYPE",
                "Duplicate reusable scaledInt name: " + parsedScaledInt.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedArray.name(),
          "SEMANTIC_INVALID_ARRAY_NAME",
          "Array type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedArray.name(), context);
      if (context.reusableArrayByName.putIfAbsent(parsedArray.name(), parsedArray) != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_ARRAY_TYPE",
                "Duplicate reusable array name: " + parsedArray.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedVector.name(),
          "SEMANTIC_INVALID_VECTOR_NAME",
          "Vector type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVector.name(), context);
      if (context.reusableVectorByName.putIfAbsent(parsedVector.name(), parsedVector) != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_VECTOR_TYPE",
                "Duplicate reusable vector name: " + parsedVector.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedBlobArray.name(),
          "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
          "blobArray type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobArray.name(), context);
      if (context.reusableBlobArrayByName.putIfAbsent(parsedBlobArray.name(), parsedBlobArray)
          != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE",
                "Duplicate reusable blobArray name: " + parsedBlobArray.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedBlobVector.name(),
          "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
          "blobVector type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobVector.name(), context);
      if (context.reusableBlobVectorByName.putIfAbsent(parsedBlobVector.name(), parsedBlobVector)
          != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE",
                "Duplicate reusable blobVector name: " + parsedBlobVector.name(),
                context.sourcePath));
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
      SemanticValidationRules.validateIdentifier(
          parsedVarString.name(),
          "SEMANTIC_INVALID_VAR_STRING_NAME",
          "varString type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVarString.name(), context);
      if (context.reusableVarStringByName.putIfAbsent(parsedVarString.name(), parsedVarString)
          != null) {
        context.diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_VAR_STRING_TYPE",
                "Duplicate reusable varString name: " + parsedVarString.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers one type name in the schema-level global type set.
   *
   * @param typeName type name being registered
   * @param context shared semantic-resolution state
   */
  private static void registerGlobalTypeName(String typeName, ResolutionContext context) {
    if (!context.globalTypeNames.add(typeName)) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_TYPE_NAME",
              "Duplicate global type name: " + typeName,
              context.sourcePath));
    }
  }
}
