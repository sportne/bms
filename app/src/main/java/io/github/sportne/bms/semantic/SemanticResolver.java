package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.util.Diagnostics;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Converts parsed XML objects into resolved objects used by generators.
 *
 * <p>This stage performs checks that XSD cannot fully enforce, such as namespace format, duplicate
 * names, unknown type references, and numeric-slice validation rules.
 */
public final class SemanticResolver {
  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern NAMESPACE_PATTERN =
      Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

  /**
   * Builds the resolved schema.
   *
   * <p>If any semantic error exists, this method throws a {@link BmsException} with diagnostics.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param sourcePath human-readable source path used in diagnostics
   * @return resolved schema ready for code generation
   * @throws BmsException if one or more semantic errors are found
   */
  public ResolvedSchema resolve(ParsedSchema parsedSchema, String sourcePath) throws BmsException {
    ResolutionContext context = new ResolutionContext(sourcePath);
    validateNamespace(
        parsedSchema.namespace(), "schema@namespace", context.sourcePath, context.diagnostics);
    registerSchemaLevelTypes(parsedSchema, context);

    ReusableResolution reusableResolution = resolveReusableDefinitions(parsedSchema, context);
    List<ResolvedMessageType> resolvedMessageTypes =
        resolveMessageTypes(parsedSchema.namespace(), parsedSchema.messageTypes(), context);

    throwIfDiagnosticsContainErrors(context.diagnostics);
    return new ResolvedSchema(
        parsedSchema.namespace(),
        resolvedMessageTypes,
        reusableResolution.reusableBitFields(),
        reusableResolution.reusableFloats(),
        reusableResolution.reusableScaledInts(),
        reusableResolution.reusableArrays(),
        reusableResolution.reusableVectors(),
        reusableResolution.reusableBlobArrays(),
        reusableResolution.reusableBlobVectors(),
        reusableResolution.reusableVarStrings(),
        reusableResolution.reusableChecksums(),
        reusableResolution.reusablePads());
  }

  /**
   * Registers all schema-level named type definitions for reference lookup and duplicate checks.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param context shared semantic-resolution state
   */
  private static void registerSchemaLevelTypes(
      ParsedSchema parsedSchema, ResolutionContext context) {
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
   * Registers message-type names and validates message-specific metadata.
   *
   * @param messageTypes parsed message definitions
   * @param context shared semantic-resolution state
   */
  private static void registerMessageTypes(
      List<ParsedMessageType> messageTypes, ResolutionContext context) {
    for (ParsedMessageType messageType : messageTypes) {
      validateIdentifier(
          messageType.name(),
          "SEMANTIC_INVALID_MESSAGE_NAME",
          "Message type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      if (context.messageTypeByName.putIfAbsent(messageType.name(), messageType) != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_MESSAGE_TYPE",
                "Duplicate message type name: " + messageType.name(),
                context.sourcePath));
      }
      registerGlobalTypeName(messageType.name(), context);
      if (messageType.namespaceOverride() != null) {
        validateNamespace(
            messageType.namespaceOverride(),
            "messageType@namespace",
            context.sourcePath,
            context.diagnostics);
      }
    }
  }

  /**
   * Registers reusable float definitions and validates their type names.
   *
   * @param reusableFloats parsed reusable float definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableFloats(
      List<ParsedFloat> reusableFloats, ResolutionContext context) {
    for (ParsedFloat parsedFloat : reusableFloats) {
      validateIdentifier(
          parsedFloat.name(),
          "SEMANTIC_INVALID_FLOAT_NAME",
          "Float type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedFloat.name(), context);
      if (context.reusableFloatByName.putIfAbsent(parsedFloat.name(), parsedFloat) != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_FLOAT_TYPE",
                "Duplicate reusable float name: " + parsedFloat.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable scaled-int definitions and validates their type names.
   *
   * @param reusableScaledInts parsed reusable scaled-int definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableScaledInts(
      List<ParsedScaledInt> reusableScaledInts, ResolutionContext context) {
    for (ParsedScaledInt parsedScaledInt : reusableScaledInts) {
      validateIdentifier(
          parsedScaledInt.name(),
          "SEMANTIC_INVALID_SCALED_INT_NAME",
          "ScaledInt type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedScaledInt.name(), context);
      if (context.reusableScaledIntByName.putIfAbsent(parsedScaledInt.name(), parsedScaledInt)
          != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_SCALED_INT_TYPE",
                "Duplicate reusable scaledInt name: " + parsedScaledInt.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable array definitions and validates their type names.
   *
   * @param reusableArrays parsed reusable array definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableArrays(
      List<ParsedArray> reusableArrays, ResolutionContext context) {
    for (ParsedArray parsedArray : reusableArrays) {
      validateIdentifier(
          parsedArray.name(),
          "SEMANTIC_INVALID_ARRAY_NAME",
          "Array type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedArray.name(), context);
      if (context.reusableArrayByName.putIfAbsent(parsedArray.name(), parsedArray) != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_ARRAY_TYPE",
                "Duplicate reusable array name: " + parsedArray.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable vector definitions and validates their type names.
   *
   * @param reusableVectors parsed reusable vector definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableVectors(
      List<ParsedVector> reusableVectors, ResolutionContext context) {
    for (ParsedVector parsedVector : reusableVectors) {
      validateIdentifier(
          parsedVector.name(),
          "SEMANTIC_INVALID_VECTOR_NAME",
          "Vector type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVector.name(), context);
      if (context.reusableVectorByName.putIfAbsent(parsedVector.name(), parsedVector) != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_VECTOR_TYPE",
                "Duplicate reusable vector name: " + parsedVector.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable blob-array definitions and validates their type names.
   *
   * @param reusableBlobArrays parsed reusable blob-array definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableBlobArrays(
      List<ParsedBlobArray> reusableBlobArrays, ResolutionContext context) {
    for (ParsedBlobArray parsedBlobArray : reusableBlobArrays) {
      validateIdentifier(
          parsedBlobArray.name(),
          "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
          "blobArray type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobArray.name(), context);
      if (context.reusableBlobArrayByName.putIfAbsent(parsedBlobArray.name(), parsedBlobArray)
          != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE",
                "Duplicate reusable blobArray name: " + parsedBlobArray.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable blob-vector definitions and validates their type names.
   *
   * @param reusableBlobVectors parsed reusable blob-vector definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableBlobVectors(
      List<ParsedBlobVector> reusableBlobVectors, ResolutionContext context) {
    for (ParsedBlobVector parsedBlobVector : reusableBlobVectors) {
      validateIdentifier(
          parsedBlobVector.name(),
          "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
          "blobVector type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedBlobVector.name(), context);
      if (context.reusableBlobVectorByName.putIfAbsent(parsedBlobVector.name(), parsedBlobVector)
          != null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE",
                "Duplicate reusable blobVector name: " + parsedBlobVector.name(),
                context.sourcePath));
      }
    }
  }

  /**
   * Registers reusable varString definitions and validates their type names.
   *
   * @param reusableVarStrings parsed reusable varString definitions
   * @param context shared semantic-resolution state
   */
  private static void registerReusableVarStrings(
      List<ParsedVarString> reusableVarStrings, ResolutionContext context) {
    for (ParsedVarString parsedVarString : reusableVarStrings) {
      validateIdentifier(
          parsedVarString.name(),
          "SEMANTIC_INVALID_VAR_STRING_NAME",
          "varString type name must be a valid identifier: ",
          context.sourcePath,
          context.diagnostics);
      registerGlobalTypeName(parsedVarString.name(), context);
      if (context.reusableVarStringByName.putIfAbsent(parsedVarString.name(), parsedVarString)
          != null) {
        context.diagnostics.add(
            error(
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
          error(
              "SEMANTIC_DUPLICATE_TYPE_NAME",
              "Duplicate global type name: " + typeName,
              context.sourcePath));
    }
  }

  /**
   * Resolves all schema-level reusable definitions after registration.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param context shared semantic-resolution state
   * @return reusable-definition resolution result
   */
  private static ReusableResolution resolveReusableDefinitions(
      ParsedSchema parsedSchema, ResolutionContext context) {
    List<ResolvedFloat> resolvedReusableFloats =
        resolveReusableFloats(parsedSchema.reusableFloats(), context);
    List<ResolvedScaledInt> resolvedReusableScaledInts =
        resolveReusableScaledInts(parsedSchema.reusableScaledInts(), context);
    List<ResolvedArray> resolvedReusableArrays =
        resolveReusableArrays(parsedSchema.reusableArrays(), context);
    List<ResolvedVector> resolvedReusableVectors =
        resolveReusableVectors(parsedSchema.reusableVectors(), context);
    List<ResolvedBlobArray> resolvedReusableBlobArrays =
        resolveReusableBlobArrays(parsedSchema.reusableBlobArrays());
    List<ResolvedBlobVector> resolvedReusableBlobVectors =
        resolveReusableBlobVectors(parsedSchema.reusableBlobVectors(), context);
    List<ResolvedVarString> resolvedReusableVarStrings =
        resolveReusableVarStrings(parsedSchema.reusableVarStrings(), context);
    List<ResolvedChecksum> resolvedReusableChecksums =
        resolveReusableChecksums(parsedSchema.reusableChecksums());
    List<ResolvedPad> resolvedReusablePads = resolveReusablePads(parsedSchema.reusablePads());
    List<ResolvedBitField> resolvedReusableBitFields =
        resolveReusableBitFields(parsedSchema.reusableBitFields(), context);

    return new ReusableResolution(
        resolvedReusableBitFields,
        resolvedReusableFloats,
        resolvedReusableScaledInts,
        resolvedReusableArrays,
        resolvedReusableVectors,
        resolvedReusableBlobArrays,
        resolvedReusableBlobVectors,
        resolvedReusableVarStrings,
        resolvedReusableChecksums,
        resolvedReusablePads);
  }

  /**
   * Resolves reusable float definitions.
   *
   * @param reusableFloats parsed reusable float definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable floats
   */
  private static List<ResolvedFloat> resolveReusableFloats(
      List<ParsedFloat> reusableFloats, ResolutionContext context) {
    List<ResolvedFloat> resolvedReusableFloats = new ArrayList<>();
    for (ParsedFloat parsedFloat : reusableFloats) {
      validateFloatScaleRules(parsedFloat, context.sourcePath, context.diagnostics);
      resolvedReusableFloats.add(
          new ResolvedFloat(
              parsedFloat.name(),
              parsedFloat.size(),
              parsedFloat.encoding(),
              parsedFloat.scale(),
              parsedFloat.endian(),
              parsedFloat.comment()));
    }
    return resolvedReusableFloats;
  }

  /**
   * Resolves reusable scaled-int definitions.
   *
   * @param reusableScaledInts parsed reusable scaled-int definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable scaled ints
   */
  private static List<ResolvedScaledInt> resolveReusableScaledInts(
      List<ParsedScaledInt> reusableScaledInts, ResolutionContext context) {
    List<ResolvedScaledInt> resolvedReusableScaledInts = new ArrayList<>();
    for (ParsedScaledInt parsedScaledInt : reusableScaledInts) {
      PrimitiveType baseType = PrimitiveType.fromSchemaName(parsedScaledInt.baseTypeName());
      if (baseType == null) {
        context.diagnostics.add(
            error(
                "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE",
                "Invalid scaledInt baseType: " + parsedScaledInt.baseTypeName(),
                context.sourcePath));
        continue;
      }
      resolvedReusableScaledInts.add(
          new ResolvedScaledInt(
              parsedScaledInt.name(),
              baseType,
              parsedScaledInt.scale(),
              parsedScaledInt.endian(),
              parsedScaledInt.comment()));
    }
    return resolvedReusableScaledInts;
  }

  /**
   * Resolves reusable array definitions.
   *
   * @param reusableArrays parsed reusable array definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable arrays
   */
  private static List<ResolvedArray> resolveReusableArrays(
      List<ParsedArray> reusableArrays, ResolutionContext context) {
    List<ResolvedArray> resolvedReusableArrays = new ArrayList<>();
    for (ParsedArray parsedArray : reusableArrays) {
      ResolvedTypeRef elementTypeRef =
          resolveTypeRef(
              parsedArray.elementTypeName(), "reusable array " + parsedArray.name(), context);
      if (elementTypeRef == null) {
        continue;
      }
      resolvedReusableArrays.add(
          new ResolvedArray(
              parsedArray.name(),
              elementTypeRef,
              parsedArray.length(),
              parsedArray.endian(),
              parsedArray.comment()));
    }
    return resolvedReusableArrays;
  }

  /**
   * Resolves reusable vector definitions.
   *
   * @param reusableVectors parsed reusable vector definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable vectors
   */
  private static List<ResolvedVector> resolveReusableVectors(
      List<ParsedVector> reusableVectors, ResolutionContext context) {
    List<ResolvedVector> resolvedReusableVectors = new ArrayList<>();
    for (ParsedVector parsedVector : reusableVectors) {
      ResolvedTypeRef elementTypeRef =
          resolveTypeRef(
              parsedVector.elementTypeName(), "reusable vector " + parsedVector.name(), context);
      ResolvedLengthMode lengthMode =
          resolveLengthMode(
              parsedVector.lengthMode(),
              "reusable vector " + parsedVector.name(),
              context.sourcePath,
              context.diagnostics,
              Collections.emptySet(),
              false,
              true);
      if (elementTypeRef == null || lengthMode == null) {
        continue;
      }
      resolvedReusableVectors.add(
          new ResolvedVector(
              parsedVector.name(),
              elementTypeRef,
              parsedVector.endian(),
              parsedVector.comment(),
              lengthMode));
    }
    return resolvedReusableVectors;
  }

  /**
   * Resolves reusable blob-array definitions.
   *
   * @param reusableBlobArrays parsed reusable blob-array definitions
   * @return resolved reusable blob arrays
   */
  private static List<ResolvedBlobArray> resolveReusableBlobArrays(
      List<ParsedBlobArray> reusableBlobArrays) {
    List<ResolvedBlobArray> resolvedReusableBlobArrays = new ArrayList<>();
    for (ParsedBlobArray parsedBlobArray : reusableBlobArrays) {
      resolvedReusableBlobArrays.add(
          new ResolvedBlobArray(
              parsedBlobArray.name(), parsedBlobArray.length(), parsedBlobArray.comment()));
    }
    return resolvedReusableBlobArrays;
  }

  /**
   * Resolves reusable blob-vector definitions.
   *
   * @param reusableBlobVectors parsed reusable blob-vector definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable blob vectors
   */
  private static List<ResolvedBlobVector> resolveReusableBlobVectors(
      List<ParsedBlobVector> reusableBlobVectors, ResolutionContext context) {
    List<ResolvedBlobVector> resolvedReusableBlobVectors = new ArrayList<>();
    for (ParsedBlobVector parsedBlobVector : reusableBlobVectors) {
      ResolvedLengthMode lengthMode =
          resolveLengthMode(
              parsedBlobVector.lengthMode(),
              "reusable blobVector " + parsedBlobVector.name(),
              context.sourcePath,
              context.diagnostics,
              Collections.emptySet(),
              false,
              false);
      if (lengthMode == null) {
        continue;
      }
      resolvedReusableBlobVectors.add(
          new ResolvedBlobVector(parsedBlobVector.name(), parsedBlobVector.comment(), lengthMode));
    }
    return resolvedReusableBlobVectors;
  }

  /**
   * Resolves reusable varString definitions.
   *
   * @param reusableVarStrings parsed reusable varString definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable varStrings
   */
  private static List<ResolvedVarString> resolveReusableVarStrings(
      List<ParsedVarString> reusableVarStrings, ResolutionContext context) {
    List<ResolvedVarString> resolvedReusableVarStrings = new ArrayList<>();
    for (ParsedVarString parsedVarString : reusableVarStrings) {
      ResolvedLengthMode lengthMode =
          resolveLengthMode(
              parsedVarString.lengthMode(),
              "reusable varString " + parsedVarString.name(),
              context.sourcePath,
              context.diagnostics,
              Collections.emptySet(),
              false,
              false);
      if (lengthMode == null) {
        continue;
      }
      resolvedReusableVarStrings.add(
          new ResolvedVarString(
              parsedVarString.name(),
              parsedVarString.encoding(),
              parsedVarString.comment(),
              lengthMode));
    }
    return resolvedReusableVarStrings;
  }

  /**
   * Resolves reusable checksum definitions.
   *
   * @param reusableChecksums parsed reusable checksum definitions
   * @return resolved reusable checksums
   */
  private static List<ResolvedChecksum> resolveReusableChecksums(
      List<ParsedChecksum> reusableChecksums) {
    List<ResolvedChecksum> resolvedReusableChecksums = new ArrayList<>();
    for (ParsedChecksum parsedChecksum : reusableChecksums) {
      resolvedReusableChecksums.add(
          new ResolvedChecksum(
              parsedChecksum.algorithm(), parsedChecksum.range(), parsedChecksum.comment()));
    }
    return resolvedReusableChecksums;
  }

  /**
   * Resolves reusable pad definitions.
   *
   * @param reusablePads parsed reusable pad definitions
   * @return resolved reusable pads
   */
  private static List<ResolvedPad> resolveReusablePads(List<ParsedPad> reusablePads) {
    List<ResolvedPad> resolvedReusablePads = new ArrayList<>();
    for (ParsedPad parsedPad : reusablePads) {
      resolvedReusablePads.add(new ResolvedPad(parsedPad.bytes(), parsedPad.comment()));
    }
    return resolvedReusablePads;
  }

  /**
   * Resolves reusable bitfield definitions and enforces unique schema-level bitfield names.
   *
   * @param reusableBitFields parsed reusable bitfield definitions
   * @param context shared semantic-resolution state
   * @return resolved reusable bitfields
   */
  private static List<ResolvedBitField> resolveReusableBitFields(
      List<ParsedBitField> reusableBitFields, ResolutionContext context) {
    Set<String> reusableBitFieldNames = new HashSet<>();
    List<ResolvedBitField> resolvedReusableBitFields = new ArrayList<>();
    for (ParsedBitField parsedBitField : reusableBitFields) {
      if (!reusableBitFieldNames.add(parsedBitField.name())) {
        context.diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_ROOT_BIT_FIELD_NAME",
                "Duplicate schema-level bitField name: " + parsedBitField.name(),
                context.sourcePath));
      }
      resolvedReusableBitFields.add(
          resolveBitField(parsedBitField, context.sourcePath, context.diagnostics));
    }
    return resolvedReusableBitFields;
  }

  /**
   * Resolves all message types and their members.
   *
   * @param schemaNamespace schema-level namespace value
   * @param messageTypes parsed message definitions
   * @param context shared semantic-resolution state
   * @return resolved message definitions
   */
  private static List<ResolvedMessageType> resolveMessageTypes(
      String schemaNamespace, List<ParsedMessageType> messageTypes, ResolutionContext context) {
    List<ResolvedMessageType> resolvedMessageTypes = new ArrayList<>();
    for (ParsedMessageType parsedMessageType : messageTypes) {
      resolvedMessageTypes.add(resolveMessageType(schemaNamespace, parsedMessageType, context));
    }
    return resolvedMessageTypes;
  }

  /**
   * Resolves one message type and preserves member declaration order.
   *
   * @param schemaNamespace schema-level namespace value
   * @param parsedMessageType parsed message type definition
   * @param context shared semantic-resolution state
   * @return resolved message type
   */
  private static ResolvedMessageType resolveMessageType(
      String schemaNamespace, ParsedMessageType parsedMessageType, ResolutionContext context) {
    String effectiveNamespace =
        parsedMessageType.namespaceOverride() == null
            ? schemaNamespace
            : parsedMessageType.namespaceOverride();

    MessageResolutionState messageState = new MessageResolutionState();
    List<ResolvedMessageMember> resolvedMembers = new ArrayList<>();
    for (ParsedMessageMember member : parsedMessageType.members()) {
      ResolvedMessageMember resolvedMember =
          resolveMessageMember(
              parsedMessageType, effectiveNamespace, member, messageState, context);
      if (resolvedMember != null) {
        resolvedMembers.add(resolvedMember);
      }
    }

    return new ResolvedMessageType(
        parsedMessageType.name(), parsedMessageType.comment(), effectiveNamespace, resolvedMembers);
  }

  /**
   * Resolves one message member object by its parsed member subtype.
   *
   * @param parsedMessageType parsed parent message definition
   * @param currentNamespace effective namespace currently active for this message
   * @param member parsed member definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveMessageMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedMessageMember member,
      MessageResolutionState messageState,
      ResolutionContext context) {
    if (member instanceof ParsedField parsedField) {
      return resolveFieldMember(parsedMessageType, parsedField, messageState, context);
    }
    if (member instanceof ParsedBitField parsedBitField) {
      return resolveBitFieldMember(parsedMessageType, parsedBitField, messageState, context);
    }
    if (member instanceof ParsedFloat parsedFloat) {
      return resolveFloatMember(parsedMessageType, parsedFloat, messageState, context);
    }
    if (member instanceof ParsedScaledInt parsedScaledInt) {
      return resolveScaledIntMember(parsedMessageType, parsedScaledInt, messageState, context);
    }
    if (member instanceof ParsedArray parsedArray) {
      return resolveArrayMember(parsedMessageType, parsedArray, messageState, context);
    }
    if (member instanceof ParsedVector parsedVector) {
      return resolveVectorMember(parsedMessageType, parsedVector, messageState, context);
    }
    if (member instanceof ParsedBlobArray parsedBlobArray) {
      return resolveBlobArrayMember(parsedMessageType, parsedBlobArray, messageState, context);
    }
    if (member instanceof ParsedBlobVector parsedBlobVector) {
      return resolveBlobVectorMember(parsedMessageType, parsedBlobVector, messageState, context);
    }
    if (member instanceof ParsedVarString parsedVarString) {
      return resolveVarStringMember(parsedMessageType, parsedVarString, messageState, context);
    }
    if (member instanceof ParsedChecksum parsedChecksum) {
      return resolveChecksumMember(parsedChecksum);
    }
    if (member instanceof ParsedPad parsedPad) {
      return resolvePadMember(parsedPad);
    }
    if (member instanceof ParsedIfBlock parsedIfBlock) {
      return resolveIfBlockMember(
          parsedMessageType, currentNamespace, parsedIfBlock, messageState, context);
    }
    return resolveNestedTypeMember(
        parsedMessageType, currentNamespace, (ParsedMessageType) member, messageState, context);
  }

  /**
   * Resolves one parsed scalar field member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedField parsed field definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved field member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveFieldMember(
      ParsedMessageType parsedMessageType,
      ParsedField parsedField,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedField.name(),
        "SEMANTIC_INVALID_FIELD_NAME",
        "Field name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    if (!messageState.namedMembers.add(parsedField.name())) {
      context.diagnostics.add(
          error(
              "SEMANTIC_DUPLICATE_FIELD_NAME",
              "Duplicate member name in message "
                  + parsedMessageType.name()
                  + ": "
                  + parsedField.name(),
              context.sourcePath));
    }

    ResolvedTypeRef typeRef =
        resolveTypeRef(parsedField.typeName(), parsedMessageType.name(), context);
    if (typeRef == null) {
      return null;
    }
    if (typeRef instanceof PrimitiveTypeRef) {
      messageState.previousPrimitiveFieldNames.add(parsedField.name());
    }

    return new ResolvedField(
        parsedField.name(),
        typeRef,
        parsedField.length(),
        parsedField.endian(),
        parsedField.fixed(),
        parsedField.comment());
  }

  /**
   * Resolves one parsed bitfield message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedBitField parsed bitfield definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved bitfield member
   */
  private static ResolvedMessageMember resolveBitFieldMember(
      ParsedMessageType parsedMessageType,
      ParsedBitField parsedBitField,
      MessageResolutionState messageState,
      ResolutionContext context) {
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedBitField.name(), messageState.namedMembers, context);
    return resolveBitField(parsedBitField, context.sourcePath, context.diagnostics);
  }

  /**
   * Resolves one parsed float message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedFloat parsed float definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved float member
   */
  private static ResolvedMessageMember resolveFloatMember(
      ParsedMessageType parsedMessageType,
      ParsedFloat parsedFloat,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedFloat.name(),
        "SEMANTIC_INVALID_FLOAT_NAME",
        "Float name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedFloat.name(), messageState.namedMembers, context);
    validateFloatScaleRules(parsedFloat, context.sourcePath, context.diagnostics);

    return new ResolvedFloat(
        parsedFloat.name(),
        parsedFloat.size(),
        parsedFloat.encoding(),
        parsedFloat.scale(),
        parsedFloat.endian(),
        parsedFloat.comment());
  }

  /**
   * Resolves one parsed scaled-int message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedScaledInt parsed scaled-int definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved scaled-int member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveScaledIntMember(
      ParsedMessageType parsedMessageType,
      ParsedScaledInt parsedScaledInt,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedScaledInt.name(),
        "SEMANTIC_INVALID_SCALED_INT_NAME",
        "ScaledInt name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedScaledInt.name(), messageState.namedMembers, context);

    PrimitiveType baseType = PrimitiveType.fromSchemaName(parsedScaledInt.baseTypeName());
    if (baseType == null) {
      context.diagnostics.add(
          error(
              "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE",
              "Invalid scaledInt baseType in message "
                  + parsedMessageType.name()
                  + ": "
                  + parsedScaledInt.baseTypeName(),
              context.sourcePath));
      return null;
    }

    return new ResolvedScaledInt(
        parsedScaledInt.name(),
        baseType,
        parsedScaledInt.scale(),
        parsedScaledInt.endian(),
        parsedScaledInt.comment());
  }

  /**
   * Resolves one parsed array message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedArray parsed array definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved array member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveArrayMember(
      ParsedMessageType parsedMessageType,
      ParsedArray parsedArray,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedArray.name(),
        "SEMANTIC_INVALID_ARRAY_NAME",
        "Array name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedArray.name(), messageState.namedMembers, context);

    ResolvedTypeRef elementTypeRef =
        resolveTypeRef(
            parsedArray.elementTypeName(),
            "array " + parsedArray.name() + " in message " + parsedMessageType.name(),
            context);
    if (elementTypeRef == null) {
      return null;
    }

    return new ResolvedArray(
        parsedArray.name(),
        elementTypeRef,
        parsedArray.length(),
        parsedArray.endian(),
        parsedArray.comment());
  }

  /**
   * Resolves one parsed vector message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedVector parsed vector definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved vector member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveVectorMember(
      ParsedMessageType parsedMessageType,
      ParsedVector parsedVector,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedVector.name(),
        "SEMANTIC_INVALID_VECTOR_NAME",
        "Vector name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedVector.name(), messageState.namedMembers, context);

    ResolvedTypeRef elementTypeRef =
        resolveTypeRef(
            parsedVector.elementTypeName(),
            "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
            context);
    ResolvedLengthMode lengthMode =
        resolveLengthMode(
            parsedVector.lengthMode(),
            "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
            context.sourcePath,
            context.diagnostics,
            messageState.previousPrimitiveFieldNames,
            true,
            true);
    if (elementTypeRef == null || lengthMode == null) {
      return null;
    }

    return new ResolvedVector(
        parsedVector.name(),
        elementTypeRef,
        parsedVector.endian(),
        parsedVector.comment(),
        lengthMode);
  }

  /**
   * Resolves one parsed blob-array message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedBlobArray parsed blob-array definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved blob-array member
   */
  private static ResolvedMessageMember resolveBlobArrayMember(
      ParsedMessageType parsedMessageType,
      ParsedBlobArray parsedBlobArray,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedBlobArray.name(),
        "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
        "blobArray name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedBlobArray.name(), messageState.namedMembers, context);

    return new ResolvedBlobArray(
        parsedBlobArray.name(), parsedBlobArray.length(), parsedBlobArray.comment());
  }

  /**
   * Resolves one parsed blob-vector message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedBlobVector parsed blob-vector definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved blob-vector member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveBlobVectorMember(
      ParsedMessageType parsedMessageType,
      ParsedBlobVector parsedBlobVector,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedBlobVector.name(),
        "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
        "blobVector name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedBlobVector.name(), messageState.namedMembers, context);

    ResolvedLengthMode lengthMode =
        resolveLengthMode(
            parsedBlobVector.lengthMode(),
            "blobVector " + parsedBlobVector.name() + " in message " + parsedMessageType.name(),
            context.sourcePath,
            context.diagnostics,
            messageState.previousPrimitiveFieldNames,
            true,
            false);
    if (lengthMode == null) {
      return null;
    }

    return new ResolvedBlobVector(parsedBlobVector.name(), parsedBlobVector.comment(), lengthMode);
  }

  /**
   * Resolves one parsed varString message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedVarString parsed varString definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved varString member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveVarStringMember(
      ParsedMessageType parsedMessageType,
      ParsedVarString parsedVarString,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        parsedVarString.name(),
        "SEMANTIC_INVALID_VAR_STRING_NAME",
        "varString name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), parsedVarString.name(), messageState.namedMembers, context);

    ResolvedLengthMode lengthMode =
        resolveLengthMode(
            parsedVarString.lengthMode(),
            "varString " + parsedVarString.name() + " in message " + parsedMessageType.name(),
            context.sourcePath,
            context.diagnostics,
            messageState.previousPrimitiveFieldNames,
            true,
            false);
    if (lengthMode == null) {
      return null;
    }

    return new ResolvedVarString(
        parsedVarString.name(), parsedVarString.encoding(), parsedVarString.comment(), lengthMode);
  }

  /**
   * Resolves one parsed checksum member.
   *
   * @param parsedChecksum parsed checksum definition
   * @return resolved checksum member
   */
  private static ResolvedMessageMember resolveChecksumMember(ParsedChecksum parsedChecksum) {
    return new ResolvedChecksum(
        parsedChecksum.algorithm(), parsedChecksum.range(), parsedChecksum.comment());
  }

  /**
   * Resolves one parsed pad member.
   *
   * @param parsedPad parsed pad definition
   * @return resolved pad member
   */
  private static ResolvedMessageMember resolvePadMember(ParsedPad parsedPad) {
    return new ResolvedPad(parsedPad.bytes(), parsedPad.comment());
  }

  /**
   * Resolves one parsed conditional block.
   *
   * @param parsedMessageType parsed parent message definition
   * @param currentNamespace effective namespace currently active for this message
   * @param parsedIfBlock parsed conditional block
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved conditional block
   */
  private static ResolvedMessageMember resolveIfBlockMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedIfBlock parsedIfBlock,
      MessageResolutionState messageState,
      ResolutionContext context) {
    if (parsedIfBlock.test().isBlank()) {
      context.diagnostics.add(
          error(
              "SEMANTIC_INVALID_IF_TEST",
              "if@test must not be blank in message " + parsedMessageType.name() + ".",
              context.sourcePath));
    }
    if (parsedIfBlock.members().isEmpty()) {
      context.diagnostics.add(
          error(
              "SEMANTIC_EMPTY_IF_BLOCK",
              "if block in message " + parsedMessageType.name() + " must contain members.",
              context.sourcePath));
    }

    MessageResolutionState ifState =
        new MessageResolutionState(messageState.previousPrimitiveFieldNames);
    List<ResolvedMessageMember> resolvedMembers = new ArrayList<>();
    for (ParsedMessageMember nestedMember : parsedIfBlock.members()) {
      ResolvedMessageMember resolvedMember =
          resolveMessageMember(parsedMessageType, currentNamespace, nestedMember, ifState, context);
      if (resolvedMember != null) {
        resolvedMembers.add(resolvedMember);
      }
    }
    return new ResolvedIfBlock(parsedIfBlock.test(), resolvedMembers);
  }

  /**
   * Resolves one nested parsed {@code type} member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param currentNamespace effective namespace currently active for this message
   * @param nestedType parsed nested message definition
   * @param messageState per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved nested message member
   */
  private static ResolvedMessageMember resolveNestedTypeMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedMessageType nestedType,
      MessageResolutionState messageState,
      ResolutionContext context) {
    validateIdentifier(
        nestedType.name(),
        "SEMANTIC_INVALID_NESTED_TYPE_NAME",
        "Nested type name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        context.sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(), nestedType.name(), messageState.namedMembers, context);

    if (nestedType.namespaceOverride() != null) {
      validateNamespace(
          nestedType.namespaceOverride(),
          "type@namespace",
          context.sourcePath,
          context.diagnostics);
    }
    if (nestedType.members().isEmpty()) {
      context.diagnostics.add(
          error(
              "SEMANTIC_EMPTY_NESTED_TYPE",
              "Nested type "
                  + nestedType.name()
                  + " in message "
                  + parsedMessageType.name()
                  + " must contain members.",
              context.sourcePath));
    }

    return resolveMessageType(currentNamespace, nestedType, context);
  }

  /**
   * Adds a duplicate-member diagnostic when a message member name is repeated.
   *
   * @param messageTypeName parent message name
   * @param memberName member name being registered
   * @param knownMemberNames previously seen names in this message
   * @param context shared semantic-resolution state
   */
  private static void registerDuplicateMemberName(
      String messageTypeName,
      String memberName,
      Set<String> knownMemberNames,
      ResolutionContext context) {
    if (!knownMemberNames.add(memberName)) {
      context.diagnostics.add(
          error(
              "SEMANTIC_DUPLICATE_MEMBER_NAME",
              "Duplicate member name in message " + messageTypeName + ": " + memberName,
              context.sourcePath));
    }
  }

  /**
   * Throws a semantic-validation exception when any error diagnostics were collected.
   *
   * @param diagnostics semantic diagnostics list
   * @throws BmsException if one or more error diagnostics are present
   */
  private static void throwIfDiagnosticsContainErrors(List<Diagnostic> diagnostics)
      throws BmsException {
    if (Diagnostics.hasErrors(diagnostics)) {
      throw new BmsException("Semantic validation failed.", diagnostics);
    }
  }

  /**
   * Resolves a parsed type name to a concrete resolved type reference.
   *
   * @param typeName type name from XML
   * @param ownerContext owning context used in diagnostics
   * @param context shared semantic-resolution state
   * @return resolved type reference, or {@code null} when unresolved
   */
  private static ResolvedTypeRef resolveTypeRef(
      String typeName, String ownerContext, ResolutionContext context) {
    PrimitiveType primitiveType = PrimitiveType.fromSchemaName(typeName);
    if (primitiveType != null) {
      return new PrimitiveTypeRef(primitiveType);
    }
    if (context.messageTypeByName.containsKey(typeName)) {
      return new MessageTypeRef(typeName);
    }
    if (context.reusableFloatByName.containsKey(typeName)) {
      return new FloatTypeRef(typeName);
    }
    if (context.reusableScaledIntByName.containsKey(typeName)) {
      return new ScaledIntTypeRef(typeName);
    }
    if (context.reusableArrayByName.containsKey(typeName)) {
      return new ArrayTypeRef(typeName);
    }
    if (context.reusableVectorByName.containsKey(typeName)) {
      return new VectorTypeRef(typeName);
    }
    if (context.reusableBlobArrayByName.containsKey(typeName)) {
      return new BlobArrayTypeRef(typeName);
    }
    if (context.reusableBlobVectorByName.containsKey(typeName)) {
      return new BlobVectorTypeRef(typeName);
    }
    if (context.reusableVarStringByName.containsKey(typeName)) {
      return new VarStringTypeRef(typeName);
    }

    context.diagnostics.add(
        error(
            "SEMANTIC_UNKNOWN_TYPE",
            "Unknown type in " + ownerContext + ": " + typeName,
            context.sourcePath));
    return null;
  }

  /**
   * Resolves one parsed bitfield, including nested validation and conversion.
   *
   * @param parsedBitField parsed bitfield object
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved bitfield object
   */
  private static ResolvedBitField resolveBitField(
      ParsedBitField parsedBitField, String sourcePath, List<Diagnostic> diagnostics) {
    validateIdentifier(
        parsedBitField.name(),
        "SEMANTIC_INVALID_BIT_FIELD_NAME",
        "bitField name must be a valid identifier: ",
        sourcePath,
        diagnostics);

    int bitWidth = parsedBitField.size().bitWidth();
    Set<String> usedNames = new HashSet<>();
    List<ResolvedBitFlag> resolvedFlags =
        resolveBitFlags(parsedBitField, bitWidth, usedNames, sourcePath, diagnostics);
    List<ResolvedBitSegment> resolvedSegments =
        resolveBitSegments(parsedBitField, bitWidth, usedNames, sourcePath, diagnostics);

    return new ResolvedBitField(
        parsedBitField.name(),
        parsedBitField.size(),
        parsedBitField.endian(),
        parsedBitField.comment(),
        resolvedFlags,
        resolvedSegments);
  }

  /**
   * Resolves all parsed flags within a bitfield.
   *
   * @param parsedBitField parsed parent bitfield definition
   * @param bitWidth total number of bits in the parent bitfield
   * @param usedNames names already seen in this bitfield
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved flag definitions
   */
  private static List<ResolvedBitFlag> resolveBitFlags(
      ParsedBitField parsedBitField,
      int bitWidth,
      Set<String> usedNames,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    Set<String> flagNames = new HashSet<>();
    Set<Integer> flagPositions = new HashSet<>();
    List<ResolvedBitFlag> resolvedFlags = new ArrayList<>();

    for (ParsedBitFlag parsedFlag : parsedBitField.flags()) {
      validateIdentifier(
          parsedFlag.name(),
          "SEMANTIC_INVALID_FLAG_NAME",
          "Flag name must be a valid identifier: ",
          sourcePath,
          diagnostics);

      if (!flagNames.add(parsedFlag.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_FLAG_NAME",
                "Duplicate flag name: " + parsedFlag.name(),
                sourcePath));
      }
      registerDuplicateBitMemberName(
          parsedBitField.name(), parsedFlag.name(), usedNames, sourcePath, diagnostics);
      validateFlagPosition(
          parsedBitField, bitWidth, parsedFlag, flagPositions, sourcePath, diagnostics);

      resolvedFlags.add(
          new ResolvedBitFlag(parsedFlag.name(), parsedFlag.position(), parsedFlag.comment()));
    }
    return resolvedFlags;
  }

  /**
   * Resolves all parsed segments within a bitfield.
   *
   * @param parsedBitField parsed parent bitfield definition
   * @param bitWidth total number of bits in the parent bitfield
   * @param usedNames names already seen in this bitfield
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved segment definitions
   */
  private static List<ResolvedBitSegment> resolveBitSegments(
      ParsedBitField parsedBitField,
      int bitWidth,
      Set<String> usedNames,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    Set<String> segmentNames = new HashSet<>();
    List<ResolvedBitSegment> resolvedSegments = new ArrayList<>();

    for (ParsedBitSegment parsedSegment : parsedBitField.segments()) {
      validateIdentifier(
          parsedSegment.name(),
          "SEMANTIC_INVALID_SEGMENT_NAME",
          "Segment name must be a valid identifier: ",
          sourcePath,
          diagnostics);

      if (!segmentNames.add(parsedSegment.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_SEGMENT_NAME",
                "Duplicate segment name: " + parsedSegment.name(),
                sourcePath));
      }
      registerDuplicateBitMemberName(
          parsedBitField.name(), parsedSegment.name(), usedNames, sourcePath, diagnostics);
      validateSegmentRange(parsedBitField, bitWidth, parsedSegment, sourcePath, diagnostics);

      resolvedSegments.add(
          new ResolvedBitSegment(
              parsedSegment.name(),
              parsedSegment.fromBit(),
              parsedSegment.toBit(),
              parsedSegment.comment(),
              resolveBitVariants(parsedSegment, sourcePath, diagnostics)));
    }
    return resolvedSegments;
  }

  /**
   * Resolves parsed segment variants and enforces name/value constraints.
   *
   * @param parsedSegment parsed parent segment definition
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved variant definitions
   */
  private static List<ResolvedBitVariant> resolveBitVariants(
      ParsedBitSegment parsedSegment, String sourcePath, List<Diagnostic> diagnostics) {
    int segmentBitCount = parsedSegment.toBit() - parsedSegment.fromBit() + 1;
    BigInteger maxAllowedValue =
        BigInteger.ONE.shiftLeft(Math.max(segmentBitCount, 0)).subtract(BigInteger.ONE);

    Set<String> variantNames = new HashSet<>();
    List<ResolvedBitVariant> resolvedVariants = new ArrayList<>();
    for (ParsedBitVariant parsedVariant : parsedSegment.variants()) {
      validateIdentifier(
          parsedVariant.name(),
          "SEMANTIC_INVALID_VARIANT_NAME",
          "Variant name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!variantNames.add(parsedVariant.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_VARIANT_NAME",
                "Duplicate variant name in segment "
                    + parsedSegment.name()
                    + ": "
                    + parsedVariant.name(),
                sourcePath));
      }
      if (parsedVariant.value().compareTo(maxAllowedValue) > 0) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_VARIANT_VALUE",
                "Variant value "
                    + parsedVariant.value()
                    + " is too large for segment "
                    + parsedSegment.name()
                    + " ("
                    + segmentBitCount
                    + " bits).",
                sourcePath));
      }

      resolvedVariants.add(
          new ResolvedBitVariant(
              parsedVariant.name(), parsedVariant.value(), parsedVariant.comment()));
    }
    return resolvedVariants;
  }

  /**
   * Adds a duplicate-member diagnostic when a bitfield member name is repeated.
   *
   * @param bitFieldName parent bitfield name
   * @param memberName member name being registered
   * @param usedNames names already seen in this bitfield
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void registerDuplicateBitMemberName(
      String bitFieldName,
      String memberName,
      Set<String> usedNames,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (!usedNames.add(memberName)) {
      diagnostics.add(
          error(
              "SEMANTIC_DUPLICATE_BIT_MEMBER_NAME",
              "Duplicate bitField member name in " + bitFieldName + ": " + memberName,
              sourcePath));
    }
  }

  /**
   * Validates one flag position against the parent bitfield width and used positions.
   *
   * @param parsedBitField parsed parent bitfield definition
   * @param bitWidth total number of bits in the parent bitfield
   * @param parsedFlag parsed flag definition
   * @param knownPositions previously seen bit positions
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void validateFlagPosition(
      ParsedBitField parsedBitField,
      int bitWidth,
      ParsedBitFlag parsedFlag,
      Set<Integer> knownPositions,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (parsedFlag.position() >= bitWidth) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_BIT_POSITION",
              "Flag position "
                  + parsedFlag.position()
                  + " is outside bitField size "
                  + parsedBitField.size().xmlValue()
                  + " for bitField "
                  + parsedBitField.name(),
              sourcePath));
    }
    if (!knownPositions.add(parsedFlag.position())) {
      diagnostics.add(
          error(
              "SEMANTIC_DUPLICATE_FLAG_POSITION",
              "Duplicate flag position: " + parsedFlag.position(),
              sourcePath));
    }
  }

  /**
   * Validates one segment range against ordering and parent bitfield width rules.
   *
   * @param parsedBitField parsed parent bitfield definition
   * @param bitWidth total number of bits in the parent bitfield
   * @param parsedSegment parsed segment definition
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void validateSegmentRange(
      ParsedBitField parsedBitField,
      int bitWidth,
      ParsedBitSegment parsedSegment,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (parsedSegment.fromBit() > parsedSegment.toBit()) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_SEGMENT_RANGE",
              "Segment range must satisfy from <= to for segment " + parsedSegment.name(),
              sourcePath));
    }
    if (parsedSegment.fromBit() >= bitWidth || parsedSegment.toBit() >= bitWidth) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_SEGMENT_RANGE",
              "Segment "
                  + parsedSegment.name()
                  + " is outside bitField size "
                  + parsedBitField.size().xmlValue(),
              sourcePath));
    }
  }

  /**
   * Shared context object used while resolving one schema.
   *
   * <p>All mutable lookup tables and diagnostics are centralized here so helper methods stay
   * focused and have short parameter lists.
   */
  private static final class ResolutionContext {
    private final String sourcePath;
    private final List<Diagnostic> diagnostics;
    private final Map<String, ParsedMessageType> messageTypeByName;
    private final Map<String, ParsedFloat> reusableFloatByName;
    private final Map<String, ParsedScaledInt> reusableScaledIntByName;
    private final Map<String, ParsedArray> reusableArrayByName;
    private final Map<String, ParsedVector> reusableVectorByName;
    private final Map<String, ParsedBlobArray> reusableBlobArrayByName;
    private final Map<String, ParsedBlobVector> reusableBlobVectorByName;
    private final Map<String, ParsedVarString> reusableVarStringByName;
    private final Set<String> globalTypeNames;

    /**
     * Creates a fresh per-schema resolution context.
     *
     * @param sourcePath human-readable source path used in diagnostics
     */
    private ResolutionContext(String sourcePath) {
      this.sourcePath = sourcePath;
      diagnostics = new ArrayList<>();
      messageTypeByName = new LinkedHashMap<>();
      reusableFloatByName = new LinkedHashMap<>();
      reusableScaledIntByName = new LinkedHashMap<>();
      reusableArrayByName = new LinkedHashMap<>();
      reusableVectorByName = new LinkedHashMap<>();
      reusableBlobArrayByName = new LinkedHashMap<>();
      reusableBlobVectorByName = new LinkedHashMap<>();
      reusableVarStringByName = new LinkedHashMap<>();
      globalTypeNames = new HashSet<>();
    }
  }

  /**
   * Groups resolved reusable definitions so the main resolver method can return them together.
   *
   * @param reusableBitFields resolved schema-level reusable bitfields
   * @param reusableFloats resolved schema-level reusable floats
   * @param reusableScaledInts resolved schema-level reusable scaled-ints
   * @param reusableArrays resolved schema-level reusable arrays
   * @param reusableVectors resolved schema-level reusable vectors
   * @param reusableBlobArrays resolved schema-level reusable blob arrays
   * @param reusableBlobVectors resolved schema-level reusable blob vectors
   * @param reusableVarStrings resolved schema-level reusable varStrings
   * @param reusableChecksums resolved schema-level reusable checksums
   * @param reusablePads resolved schema-level reusable pads
   */
  private record ReusableResolution(
      List<ResolvedBitField> reusableBitFields,
      List<ResolvedFloat> reusableFloats,
      List<ResolvedScaledInt> reusableScaledInts,
      List<ResolvedArray> reusableArrays,
      List<ResolvedVector> reusableVectors,
      List<ResolvedBlobArray> reusableBlobArrays,
      List<ResolvedBlobVector> reusableBlobVectors,
      List<ResolvedVarString> reusableVarStrings,
      List<ResolvedChecksum> reusableChecksums,
      List<ResolvedPad> reusablePads) {}

  /** Per-message state used while resolving message members in declaration order. */
  private static final class MessageResolutionState {
    private final Set<String> namedMembers = new HashSet<>();
    private final Set<String> previousPrimitiveFieldNames = new HashSet<>();

    /**
     * Creates an empty message-resolution state.
     *
     * <p>Use this for full message scopes where no inherited count-field names are needed.
     */
    private MessageResolutionState() {}

    /**
     * Creates a nested-scope state that inherits earlier primitive count-field names.
     *
     * @param inheritedCountFields primitive scalar field names visible to this nested scope
     */
    private MessageResolutionState(Set<String> inheritedCountFields) {
      previousPrimitiveFieldNames.addAll(inheritedCountFields);
    }
  }

  /**
   * Resolves one parsed length mode and enforces count-field/terminator rules.
   *
   * @param parsedLengthMode parsed mode object
   * @param ownerContext owning context used in diagnostics
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @param knownCountFields known earlier primitive field names (message-level only)
   * @param strictCountFieldRef whether count-field references must resolve to earlier primitive
   *     fields
   * @param allowTerminatorField whether terminator-field mode is valid in this context
   * @return resolved length mode, or {@code null} when invalid
   */
  private static ResolvedLengthMode resolveLengthMode(
      ParsedLengthMode parsedLengthMode,
      String ownerContext,
      String sourcePath,
      List<Diagnostic> diagnostics,
      Set<String> knownCountFields,
      boolean strictCountFieldRef,
      boolean allowTerminatorField) {
    if (parsedLengthMode instanceof ParsedCountFieldLength parsedCountFieldLength) {
      validateIdentifier(
          parsedCountFieldLength.ref(),
          "SEMANTIC_INVALID_COUNT_FIELD_REF",
          "countField ref must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (strictCountFieldRef && !knownCountFields.contains(parsedCountFieldLength.ref())) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_COUNT_FIELD_REF",
                "countField ref in "
                    + ownerContext
                    + " must point to an earlier scalar integer field: "
                    + parsedCountFieldLength.ref(),
                sourcePath));
      }
      return new ResolvedCountFieldLength(parsedCountFieldLength.ref());
    }

    if (parsedLengthMode instanceof ParsedTerminatorValueLength parsedTerminatorValueLength) {
      return new ResolvedTerminatorValueLength(parsedTerminatorValueLength.value());
    }

    if (!allowTerminatorField) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_LENGTH_MODE",
              ownerContext + " does not allow terminatorField length mode.",
              sourcePath));
      return null;
    }

    ParsedTerminatorField parsedTerminatorField = (ParsedTerminatorField) parsedLengthMode;
    if (!terminatorPathEndsInMatch(parsedTerminatorField)) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_TERMINATOR_FIELD_PATH",
              "terminatorField path in " + ownerContext + " must end in terminatorMatch.",
              sourcePath));
    }
    return resolveTerminatorField(parsedTerminatorField, sourcePath, diagnostics);
  }

  /**
   * Resolves one recursive parsed terminator-field node.
   *
   * @param parsedTerminatorField parsed terminator-field node
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved terminator-field node
   */
  private static ResolvedTerminatorField resolveTerminatorField(
      ParsedTerminatorField parsedTerminatorField,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    validateIdentifier(
        parsedTerminatorField.name(),
        "SEMANTIC_INVALID_TERMINATOR_FIELD_NAME",
        "terminatorField name must be a valid identifier: ",
        sourcePath,
        diagnostics);

    ParsedTerminatorNode parsedNext = parsedTerminatorField.next();
    if (parsedNext == null) {
      return new ResolvedTerminatorField(parsedTerminatorField.name(), null);
    }

    ResolvedTerminatorNode resolvedNext =
        parsedNext instanceof ParsedTerminatorField parsedNestedTerminatorField
            ? resolveTerminatorField(parsedNestedTerminatorField, sourcePath, diagnostics)
            : new ResolvedTerminatorMatch(((ParsedTerminatorMatch) parsedNext).value());
    return new ResolvedTerminatorField(parsedTerminatorField.name(), resolvedNext);
  }

  /**
   * Checks whether a parsed terminator path eventually reaches a terminator-match node.
   *
   * @param parsedTerminatorField parsed path root
   * @return {@code true} when the path ends in a match node
   */
  private static boolean terminatorPathEndsInMatch(ParsedTerminatorField parsedTerminatorField) {
    ParsedTerminatorNode next = parsedTerminatorField.next();
    if (next == null) {
      return false;
    }
    if (next instanceof ParsedTerminatorMatch) {
      return true;
    }
    return terminatorPathEndsInMatch((ParsedTerminatorField) next);
  }

  /**
   * Validates scale rules for float definitions.
   *
   * @param parsedFloat parsed float object
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void validateFloatScaleRules(
      ParsedFloat parsedFloat, String sourcePath, List<Diagnostic> diagnostics) {
    if (parsedFloat.encoding() == io.github.sportne.bms.model.FloatEncoding.SCALED
        && parsedFloat.scale() == null) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_FLOAT_SCALE",
              "Float " + parsedFloat.name() + " uses scaled encoding but has no scale value.",
              sourcePath));
    }
    if (parsedFloat.encoding() == io.github.sportne.bms.model.FloatEncoding.IEEE754
        && parsedFloat.scale() != null) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_FLOAT_SCALE",
              "Float " + parsedFloat.name() + " uses ieee754 encoding and must not define scale.",
              sourcePath));
    }
  }

  /**
   * Validates that a value matches identifier syntax.
   *
   * @param value value being checked
   * @param code diagnostic code to use on failure
   * @param messagePrefix message prefix used on failure
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void validateIdentifier(
      String value,
      String code,
      String messagePrefix,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
      diagnostics.add(error(code, messagePrefix + value, sourcePath));
    }
  }

  /**
   * Validates that a namespace is non-blank and dot-delimited identifier segments.
   *
   * @param namespace namespace value to validate
   * @param attributeName attribute label used in diagnostics
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   */
  private static void validateNamespace(
      String namespace, String attributeName, String sourcePath, List<Diagnostic> diagnostics) {
    if (namespace == null || namespace.isBlank()) {
      diagnostics.add(
          error("SEMANTIC_INVALID_NAMESPACE", attributeName + " must not be blank.", sourcePath));
      return;
    }
    if (!NAMESPACE_PATTERN.matcher(namespace).matches()) {
      diagnostics.add(
          error(
              "SEMANTIC_INVALID_NAMESPACE",
              attributeName + " must be dot-delimited identifiers. Received: " + namespace,
              sourcePath));
    }
  }

  /**
   * Builds one error-level diagnostic with unknown line/column.
   *
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @param sourcePath source path used in diagnostics
   * @return error diagnostic
   */
  private static Diagnostic error(String code, String message, String sourcePath) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, sourcePath, -1, -1);
  }
}
