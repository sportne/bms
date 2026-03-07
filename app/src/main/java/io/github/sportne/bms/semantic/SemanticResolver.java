package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.util.Diagnostics;
import java.math.BigInteger;
import java.util.ArrayList;
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
   */
  public ResolvedSchema resolve(ParsedSchema parsedSchema, String sourcePath) throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();

    validateNamespace(parsedSchema.namespace(), "schema@namespace", sourcePath, diagnostics);

    Map<String, ParsedMessageType> messageTypeByName = new LinkedHashMap<>();
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

    Map<String, ParsedFloat> reusableFloatByName = new LinkedHashMap<>();
    List<ResolvedFloat> resolvedReusableFloats = new ArrayList<>();
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

    Map<String, ParsedScaledInt> reusableScaledIntByName = new LinkedHashMap<>();
    List<ResolvedScaledInt> resolvedReusableScaledInts = new ArrayList<>();
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

    List<ResolvedBitField> resolvedReusableBitFields = new ArrayList<>();
    for (ParsedBitField parsedBitField : parsedSchema.reusableBitFields()) {
      resolvedReusableBitFields.add(resolveBitField(parsedBitField, sourcePath, diagnostics));
    }

    List<ResolvedMessageType> resolvedMessageTypes = new ArrayList<>();
    for (ParsedMessageType parsedMessageType : parsedSchema.messageTypes()) {
      String effectiveNamespace =
          parsedMessageType.namespaceOverride() == null
              ? parsedSchema.namespace()
              : parsedMessageType.namespaceOverride();

      Set<String> namedMembers = new HashSet<>();
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
              resolveFieldTypeRef(
                  parsedField.typeName(),
                  parsedMessageType.name(),
                  messageTypeByName,
                  reusableFloatByName,
                  reusableScaledIntByName,
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
          continue;
        }

        if (member instanceof ParsedBitField parsedBitField) {
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

        ParsedScaledInt parsedScaledInt = (ParsedScaledInt) member;
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
        resolvedReusableScaledInts);
  }

  private static ResolvedTypeRef resolveFieldTypeRef(
      String typeName,
      String messageName,
      Map<String, ParsedMessageType> messageTypeByName,
      Map<String, ParsedFloat> reusableFloatByName,
      Map<String, ParsedScaledInt> reusableScaledIntByName,
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

    diagnostics.add(
        error(
            "SEMANTIC_UNKNOWN_TYPE",
            "Unknown field type in message " + messageName + ": " + typeName,
            sourcePath));
    return null;
  }

  private static ResolvedBitField resolveBitField(
      ParsedBitField parsedBitField, String sourcePath, List<Diagnostic> diagnostics) {
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
                "Duplicate bitField member name: " + parsedFlag.name(),
                sourcePath));
      }
      if (parsedFlag.position() >= bitWidth) {
        diagnostics.add(
            error(
                "SEMANTIC_INVALID_BIT_POSITION",
                "Flag position "
                    + parsedFlag.position()
                    + " is outside bitField size "
                    + parsedBitField.size().xmlValue(),
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
                "Duplicate bitField member name: " + parsedSegment.name(),
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
        parsedBitField.size(),
        parsedBitField.endian(),
        parsedBitField.comment(),
        resolvedFlags,
        resolvedSegments);
  }

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

  private static Diagnostic error(String code, String message, String sourcePath) {
    return new Diagnostic(DiagnosticSeverity.ERROR, code, message, sourcePath, -1, -1);
  }
}
