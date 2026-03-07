package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
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
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorMatch;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorNode;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
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
    List<Diagnostic> diagnostics = new ArrayList<>();
    validateNamespace(parsedSchema.namespace(), "schema@namespace", sourcePath, diagnostics);

    Map<String, ParsedMessageType> messageTypeByName = new LinkedHashMap<>();
    Map<String, ParsedFloat> reusableFloatByName = new LinkedHashMap<>();
    Map<String, ParsedScaledInt> reusableScaledIntByName = new LinkedHashMap<>();
    Map<String, ParsedArray> reusableArrayByName = new LinkedHashMap<>();
    Map<String, ParsedVector> reusableVectorByName = new LinkedHashMap<>();
    Map<String, ParsedBlobArray> reusableBlobArrayByName = new LinkedHashMap<>();
    Map<String, ParsedBlobVector> reusableBlobVectorByName = new LinkedHashMap<>();
    Set<String> globalTypeNames = new HashSet<>();

    for (ParsedMessageType messageType : parsedSchema.messageTypes()) {
      validateIdentifier(
          messageType.name(),
          "SEMANTIC_INVALID_MESSAGE_NAME",
          "Message type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (messageTypeByName.putIfAbsent(messageType.name(), messageType) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_MESSAGE_TYPE",
                "Duplicate message type name: " + messageType.name(),
                sourcePath));
      }
      if (!globalTypeNames.add(messageType.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + messageType.name(),
                sourcePath));
      }
      if (messageType.namespaceOverride() != null) {
        validateNamespace(
            messageType.namespaceOverride(), "messageType@namespace", sourcePath, diagnostics);
      }
    }

    for (ParsedFloat parsedFloat : parsedSchema.reusableFloats()) {
      validateIdentifier(
          parsedFloat.name(),
          "SEMANTIC_INVALID_FLOAT_NAME",
          "Float type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedFloat.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedFloat.name(),
                sourcePath));
      }
      if (reusableFloatByName.putIfAbsent(parsedFloat.name(), parsedFloat) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_FLOAT_TYPE",
                "Duplicate reusable float name: " + parsedFloat.name(),
                sourcePath));
      }
    }

    for (ParsedScaledInt parsedScaledInt : parsedSchema.reusableScaledInts()) {
      validateIdentifier(
          parsedScaledInt.name(),
          "SEMANTIC_INVALID_SCALED_INT_NAME",
          "ScaledInt type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedScaledInt.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedScaledInt.name(),
                sourcePath));
      }
      if (reusableScaledIntByName.putIfAbsent(parsedScaledInt.name(), parsedScaledInt) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_SCALED_INT_TYPE",
                "Duplicate reusable scaledInt name: " + parsedScaledInt.name(),
                sourcePath));
      }
    }

    for (ParsedArray parsedArray : parsedSchema.reusableArrays()) {
      validateIdentifier(
          parsedArray.name(),
          "SEMANTIC_INVALID_ARRAY_NAME",
          "Array type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedArray.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedArray.name(),
                sourcePath));
      }
      if (reusableArrayByName.putIfAbsent(parsedArray.name(), parsedArray) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_ARRAY_TYPE",
                "Duplicate reusable array name: " + parsedArray.name(),
                sourcePath));
      }
    }

    for (ParsedVector parsedVector : parsedSchema.reusableVectors()) {
      validateIdentifier(
          parsedVector.name(),
          "SEMANTIC_INVALID_VECTOR_NAME",
          "Vector type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedVector.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedVector.name(),
                sourcePath));
      }
      if (reusableVectorByName.putIfAbsent(parsedVector.name(), parsedVector) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_VECTOR_TYPE",
                "Duplicate reusable vector name: " + parsedVector.name(),
                sourcePath));
      }
    }

    for (ParsedBlobArray parsedBlobArray : parsedSchema.reusableBlobArrays()) {
      validateIdentifier(
          parsedBlobArray.name(),
          "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
          "blobArray type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedBlobArray.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedBlobArray.name(),
                sourcePath));
      }
      if (reusableBlobArrayByName.putIfAbsent(parsedBlobArray.name(), parsedBlobArray) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE",
                "Duplicate reusable blobArray name: " + parsedBlobArray.name(),
                sourcePath));
      }
    }

    for (ParsedBlobVector parsedBlobVector : parsedSchema.reusableBlobVectors()) {
      validateIdentifier(
          parsedBlobVector.name(),
          "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
          "blobVector type name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!globalTypeNames.add(parsedBlobVector.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_TYPE_NAME",
                "Duplicate global type name: " + parsedBlobVector.name(),
                sourcePath));
      }
      if (reusableBlobVectorByName.putIfAbsent(parsedBlobVector.name(), parsedBlobVector) != null) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE",
                "Duplicate reusable blobVector name: " + parsedBlobVector.name(),
                sourcePath));
      }
    }

    List<ResolvedFloat> resolvedReusableFloats = new ArrayList<>();
    for (ParsedFloat parsedFloat : parsedSchema.reusableFloats()) {
      validateFloatScaleRules(parsedFloat, sourcePath, diagnostics);
      resolvedReusableFloats.add(
          new ResolvedFloat(
              parsedFloat.name(),
              parsedFloat.size(),
              parsedFloat.encoding(),
              parsedFloat.scale(),
              parsedFloat.endian(),
              parsedFloat.comment()));
    }

    List<ResolvedScaledInt> resolvedReusableScaledInts = new ArrayList<>();
    for (ParsedScaledInt parsedScaledInt : parsedSchema.reusableScaledInts()) {
      PrimitiveType baseType = PrimitiveType.fromSchemaName(parsedScaledInt.baseTypeName());
      if (baseType == null) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE",
                "Invalid scaledInt baseType: " + parsedScaledInt.baseTypeName(),
                sourcePath));
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

    List<ResolvedArray> resolvedReusableArrays = new ArrayList<>();
    for (ParsedArray parsedArray : parsedSchema.reusableArrays()) {
      ResolvedTypeRef elementTypeRef =
          resolveTypeRef(
              parsedArray.elementTypeName(),
              "reusable array " + parsedArray.name(),
              messageTypeByName,
              reusableFloatByName,
              reusableScaledIntByName,
              reusableArrayByName,
              reusableVectorByName,
              reusableBlobArrayByName,
              reusableBlobVectorByName,
              sourcePath,
              diagnostics);
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

    List<ResolvedVector> resolvedReusableVectors = new ArrayList<>();
    for (ParsedVector parsedVector : parsedSchema.reusableVectors()) {
      ResolvedTypeRef elementTypeRef =
          resolveTypeRef(
              parsedVector.elementTypeName(),
              "reusable vector " + parsedVector.name(),
              messageTypeByName,
              reusableFloatByName,
              reusableScaledIntByName,
              reusableArrayByName,
              reusableVectorByName,
              reusableBlobArrayByName,
              reusableBlobVectorByName,
              sourcePath,
              diagnostics);
      ResolvedLengthMode lengthMode =
          resolveLengthMode(
              parsedVector.lengthMode(),
              "reusable vector " + parsedVector.name(),
              sourcePath,
              diagnostics,
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

    List<ResolvedBlobArray> resolvedReusableBlobArrays = new ArrayList<>();
    for (ParsedBlobArray parsedBlobArray : parsedSchema.reusableBlobArrays()) {
      resolvedReusableBlobArrays.add(
          new ResolvedBlobArray(
              parsedBlobArray.name(), parsedBlobArray.length(), parsedBlobArray.comment()));
    }

    List<ResolvedBlobVector> resolvedReusableBlobVectors = new ArrayList<>();
    for (ParsedBlobVector parsedBlobVector : parsedSchema.reusableBlobVectors()) {
      ResolvedLengthMode lengthMode =
          resolveLengthMode(
              parsedBlobVector.lengthMode(),
              "reusable blobVector " + parsedBlobVector.name(),
              sourcePath,
              diagnostics,
              Collections.emptySet(),
              false,
              false);
      if (lengthMode == null) {
        continue;
      }
      resolvedReusableBlobVectors.add(
          new ResolvedBlobVector(parsedBlobVector.name(), parsedBlobVector.comment(), lengthMode));
    }

    Set<String> reusableBitFieldNames = new HashSet<>();
    List<ResolvedBitField> resolvedReusableBitFields = new ArrayList<>();
    for (ParsedBitField parsedBitField : parsedSchema.reusableBitFields()) {
      if (!reusableBitFieldNames.add(parsedBitField.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_ROOT_BIT_FIELD_NAME",
                "Duplicate schema-level bitField name: " + parsedBitField.name(),
                sourcePath));
      }
      resolvedReusableBitFields.add(resolveBitField(parsedBitField, sourcePath, diagnostics));
    }

    List<ResolvedMessageType> resolvedMessageTypes = new ArrayList<>();
    for (ParsedMessageType parsedMessageType : parsedSchema.messageTypes()) {
      String effectiveNamespace =
          parsedMessageType.namespaceOverride() == null
              ? parsedSchema.namespace()
              : parsedMessageType.namespaceOverride();

      Set<String> namedMembers = new HashSet<>();
      Set<String> previousPrimitiveFieldNames = new HashSet<>();
      List<ResolvedMessageMember> resolvedMembers = new ArrayList<>();
      for (ParsedMessageMember member : parsedMessageType.members()) {
        if (member instanceof ParsedField parsedField) {
          validateIdentifier(
              parsedField.name(),
              "SEMANTIC_INVALID_FIELD_NAME",
              "Field name must be a valid identifier in message " + parsedMessageType.name() + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedField.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_FIELD_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedField.name(),
                    sourcePath));
          }

          ResolvedTypeRef typeRef =
              resolveTypeRef(
                  parsedField.typeName(),
                  parsedMessageType.name(),
                  messageTypeByName,
                  reusableFloatByName,
                  reusableScaledIntByName,
                  reusableArrayByName,
                  reusableVectorByName,
                  reusableBlobArrayByName,
                  reusableBlobVectorByName,
                  sourcePath,
                  diagnostics);
          if (typeRef == null) {
            continue;
          }

          resolvedMembers.add(
              new ResolvedField(
                  parsedField.name(),
                  typeRef,
                  parsedField.length(),
                  parsedField.endian(),
                  parsedField.fixed(),
                  parsedField.comment()));
          if (typeRef instanceof PrimitiveTypeRef) {
            previousPrimitiveFieldNames.add(parsedField.name());
          }
          continue;
        }

        if (member instanceof ParsedBitField parsedBitField) {
          if (!namedMembers.add(parsedBitField.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedBitField.name(),
                    sourcePath));
          }
          resolvedMembers.add(resolveBitField(parsedBitField, sourcePath, diagnostics));
          continue;
        }

        if (member instanceof ParsedFloat parsedFloat) {
          validateIdentifier(
              parsedFloat.name(),
              "SEMANTIC_INVALID_FLOAT_NAME",
              "Float name must be a valid identifier in message " + parsedMessageType.name() + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedFloat.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedFloat.name(),
                    sourcePath));
          }
          validateFloatScaleRules(parsedFloat, sourcePath, diagnostics);
          resolvedMembers.add(
              new ResolvedFloat(
                  parsedFloat.name(),
                  parsedFloat.size(),
                  parsedFloat.encoding(),
                  parsedFloat.scale(),
                  parsedFloat.endian(),
                  parsedFloat.comment()));
          continue;
        }

        if (member instanceof ParsedScaledInt parsedScaledInt) {
          validateIdentifier(
              parsedScaledInt.name(),
              "SEMANTIC_INVALID_SCALED_INT_NAME",
              "ScaledInt name must be a valid identifier in message "
                  + parsedMessageType.name()
                  + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedScaledInt.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedScaledInt.name(),
                    sourcePath));
          }

          PrimitiveType baseType = PrimitiveType.fromSchemaName(parsedScaledInt.baseTypeName());
          if (baseType == null) {
            diagnostics.add(
                error(
                    "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE",
                    "Invalid scaledInt baseType in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedScaledInt.baseTypeName(),
                    sourcePath));
            continue;
          }

          resolvedMembers.add(
              new ResolvedScaledInt(
                  parsedScaledInt.name(),
                  baseType,
                  parsedScaledInt.scale(),
                  parsedScaledInt.endian(),
                  parsedScaledInt.comment()));
          continue;
        }

        if (member instanceof ParsedArray parsedArray) {
          validateIdentifier(
              parsedArray.name(),
              "SEMANTIC_INVALID_ARRAY_NAME",
              "Array name must be a valid identifier in message " + parsedMessageType.name() + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedArray.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedArray.name(),
                    sourcePath));
          }

          ResolvedTypeRef elementTypeRef =
              resolveTypeRef(
                  parsedArray.elementTypeName(),
                  "array " + parsedArray.name() + " in message " + parsedMessageType.name(),
                  messageTypeByName,
                  reusableFloatByName,
                  reusableScaledIntByName,
                  reusableArrayByName,
                  reusableVectorByName,
                  reusableBlobArrayByName,
                  reusableBlobVectorByName,
                  sourcePath,
                  diagnostics);
          if (elementTypeRef == null) {
            continue;
          }

          resolvedMembers.add(
              new ResolvedArray(
                  parsedArray.name(),
                  elementTypeRef,
                  parsedArray.length(),
                  parsedArray.endian(),
                  parsedArray.comment()));
          continue;
        }

        if (member instanceof ParsedVector parsedVector) {
          validateIdentifier(
              parsedVector.name(),
              "SEMANTIC_INVALID_VECTOR_NAME",
              "Vector name must be a valid identifier in message "
                  + parsedMessageType.name()
                  + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedVector.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedVector.name(),
                    sourcePath));
          }

          ResolvedTypeRef elementTypeRef =
              resolveTypeRef(
                  parsedVector.elementTypeName(),
                  "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
                  messageTypeByName,
                  reusableFloatByName,
                  reusableScaledIntByName,
                  reusableArrayByName,
                  reusableVectorByName,
                  reusableBlobArrayByName,
                  reusableBlobVectorByName,
                  sourcePath,
                  diagnostics);
          ResolvedLengthMode lengthMode =
              resolveLengthMode(
                  parsedVector.lengthMode(),
                  "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
                  sourcePath,
                  diagnostics,
                  previousPrimitiveFieldNames,
                  true,
                  true);
          if (elementTypeRef == null || lengthMode == null) {
            continue;
          }

          resolvedMembers.add(
              new ResolvedVector(
                  parsedVector.name(),
                  elementTypeRef,
                  parsedVector.endian(),
                  parsedVector.comment(),
                  lengthMode));
          continue;
        }

        if (member instanceof ParsedBlobArray parsedBlobArray) {
          validateIdentifier(
              parsedBlobArray.name(),
              "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
              "blobArray name must be a valid identifier in message "
                  + parsedMessageType.name()
                  + ": ",
              sourcePath,
              diagnostics);
          if (!namedMembers.add(parsedBlobArray.name())) {
            diagnostics.add(
                error(
                    "SEMANTIC_DUPLICATE_MEMBER_NAME",
                    "Duplicate member name in message "
                        + parsedMessageType.name()
                        + ": "
                        + parsedBlobArray.name(),
                    sourcePath));
          }
          resolvedMembers.add(
              new ResolvedBlobArray(
                  parsedBlobArray.name(), parsedBlobArray.length(), parsedBlobArray.comment()));
          continue;
        }

        ParsedBlobVector parsedBlobVector = (ParsedBlobVector) member;
        validateIdentifier(
            parsedBlobVector.name(),
            "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
            "blobVector name must be a valid identifier in message "
                + parsedMessageType.name()
                + ": ",
            sourcePath,
            diagnostics);
        if (!namedMembers.add(parsedBlobVector.name())) {
          diagnostics.add(
              error(
                  "SEMANTIC_DUPLICATE_MEMBER_NAME",
                  "Duplicate member name in message "
                      + parsedMessageType.name()
                      + ": "
                      + parsedBlobVector.name(),
                  sourcePath));
        }

        ResolvedLengthMode lengthMode =
            resolveLengthMode(
                parsedBlobVector.lengthMode(),
                "blobVector " + parsedBlobVector.name() + " in message " + parsedMessageType.name(),
                sourcePath,
                diagnostics,
                previousPrimitiveFieldNames,
                true,
                false);
        if (lengthMode == null) {
          continue;
        }
        resolvedMembers.add(
            new ResolvedBlobVector(
                parsedBlobVector.name(), parsedBlobVector.comment(), lengthMode));
      }

      resolvedMessageTypes.add(
          new ResolvedMessageType(
              parsedMessageType.name(),
              parsedMessageType.comment(),
              effectiveNamespace,
              resolvedMembers));
    }

    if (Diagnostics.hasErrors(diagnostics)) {
      throw new BmsException("Semantic validation failed.", diagnostics);
    }

    return new ResolvedSchema(
        parsedSchema.namespace(),
        resolvedMessageTypes,
        resolvedReusableBitFields,
        resolvedReusableFloats,
        resolvedReusableScaledInts,
        resolvedReusableArrays,
        resolvedReusableVectors,
        resolvedReusableBlobArrays,
        resolvedReusableBlobVectors);
  }

  /**
   * Resolves a parsed type name to a concrete resolved type reference.
   *
   * @param typeName type name from XML
   * @param ownerContext owning context used in diagnostics
   * @param messageTypeByName lookup of parsed message types
   * @param reusableFloatByName lookup of reusable float definitions
   * @param reusableScaledIntByName lookup of reusable scaled-int definitions
   * @param reusableArrayByName lookup of reusable array definitions
   * @param reusableVectorByName lookup of reusable vector definitions
   * @param reusableBlobArrayByName lookup of reusable blob-array definitions
   * @param reusableBlobVectorByName lookup of reusable blob-vector definitions
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved type reference, or {@code null} when unresolved
   */
  private static ResolvedTypeRef resolveTypeRef(
      String typeName,
      String ownerContext,
      Map<String, ParsedMessageType> messageTypeByName,
      Map<String, ParsedFloat> reusableFloatByName,
      Map<String, ParsedScaledInt> reusableScaledIntByName,
      Map<String, ParsedArray> reusableArrayByName,
      Map<String, ParsedVector> reusableVectorByName,
      Map<String, ParsedBlobArray> reusableBlobArrayByName,
      Map<String, ParsedBlobVector> reusableBlobVectorByName,
      String sourcePath,
      List<Diagnostic> diagnostics) {
    PrimitiveType primitiveType = PrimitiveType.fromSchemaName(typeName);
    if (primitiveType != null) {
      return new PrimitiveTypeRef(primitiveType);
    }
    if (messageTypeByName.containsKey(typeName)) {
      return new MessageTypeRef(typeName);
    }
    if (reusableFloatByName.containsKey(typeName)) {
      return new FloatTypeRef(typeName);
    }
    if (reusableScaledIntByName.containsKey(typeName)) {
      return new ScaledIntTypeRef(typeName);
    }
    if (reusableArrayByName.containsKey(typeName)) {
      return new ArrayTypeRef(typeName);
    }
    if (reusableVectorByName.containsKey(typeName)) {
      return new VectorTypeRef(typeName);
    }
    if (reusableBlobArrayByName.containsKey(typeName)) {
      return new BlobArrayTypeRef(typeName);
    }
    if (reusableBlobVectorByName.containsKey(typeName)) {
      return new BlobVectorTypeRef(typeName);
    }

    diagnostics.add(
        error(
            "SEMANTIC_UNKNOWN_TYPE",
            "Unknown type in " + ownerContext + ": " + typeName,
            sourcePath));
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
      if (!usedNames.add(parsedFlag.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BIT_MEMBER_NAME",
                "Duplicate bitField member name in "
                    + parsedBitField.name()
                    + ": "
                    + parsedFlag.name(),
                sourcePath));
      }
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
      if (!flagPositions.add(parsedFlag.position())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_FLAG_POSITION",
                "Duplicate flag position: " + parsedFlag.position(),
                sourcePath));
      }

      resolvedFlags.add(
          new ResolvedBitFlag(parsedFlag.name(), parsedFlag.position(), parsedFlag.comment()));
    }

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
      if (!usedNames.add(parsedSegment.name())) {
        diagnostics.add(
            error(
                "SEMANTIC_DUPLICATE_BIT_MEMBER_NAME",
                "Duplicate bitField member name in "
                    + parsedBitField.name()
                    + ": "
                    + parsedSegment.name(),
                sourcePath));
      }

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

      resolvedSegments.add(
          new ResolvedBitSegment(
              parsedSegment.name(),
              parsedSegment.fromBit(),
              parsedSegment.toBit(),
              parsedSegment.comment(),
              resolvedVariants));
    }

    return new ResolvedBitField(
        parsedBitField.name(),
        parsedBitField.size(),
        parsedBitField.endian(),
        parsedBitField.comment(),
        resolvedFlags,
        resolvedSegments);
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
