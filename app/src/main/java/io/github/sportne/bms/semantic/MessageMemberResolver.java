package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
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
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedIfBlock;
import io.github.sportne.bms.model.resolved.ResolvedIfCondition;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves message types and message members in declaration order.
 *
 * <p>This class handles field/member dispatch and message-scope rules such as duplicate names and
 * count-field ordering.
 */
final class MessageMemberResolver {
  /** Prevents instantiation of this static helper class. */
  private MessageMemberResolver() {}

  /**
   * Resolves all message types in declaration order.
   *
   * @param schemaNamespace schema-level namespace value
   * @param messageTypes parsed message definitions
   * @param context shared semantic-resolution state
   * @return resolved message definitions
   */
  static List<ResolvedMessageType> resolveMessageTypes(
      String schemaNamespace, List<ParsedMessageType> messageTypes, ResolutionContext context) {
    List<ResolvedMessageType> resolvedMessageTypes = new ArrayList<>();
    for (ParsedMessageType parsedMessageType : messageTypes) {
      resolvedMessageTypes.add(resolveMessageType(schemaNamespace, parsedMessageType, context));
    }
    return resolvedMessageTypes;
  }

  /**
   * Resolves one parsed type name to a concrete resolved type reference.
   *
   * @param typeName type name from XML
   * @param ownerContext owning context used in diagnostics
   * @param ownerNode parsed owner node used for source-path provenance
   * @param context shared semantic-resolution state
   * @return resolved type reference, or {@code null} when unresolved
   */
  static ResolvedTypeRef resolveTypeRef(
      String typeName, String ownerContext, Object ownerNode, ResolutionContext context) {
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
        SemanticValidationRules.error(
            "SEMANTIC_UNKNOWN_TYPE",
            "Unknown type in " + ownerContext + ": " + typeName,
            context.sourcePathFor(ownerNode)));
    return null;
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

    MessageResolutionContext messageContext =
        new MessageResolutionContext(collectPrimitiveFieldTypes(parsedMessageType.members()));
    List<ResolvedMessageMember> resolvedMembers = new ArrayList<>();
    for (ParsedMessageMember member : parsedMessageType.members()) {
      ResolvedMessageMember resolvedMember =
          resolveMessageMember(
              parsedMessageType, effectiveNamespace, member, messageContext, context);
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveMessageMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedMessageMember member,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    if (member instanceof ParsedField parsedField) {
      return resolveFieldMember(parsedMessageType, parsedField, messageContext, context);
    }
    if (member instanceof ParsedBitField parsedBitField) {
      return resolveBitFieldMember(parsedMessageType, parsedBitField, messageContext, context);
    }
    if (member instanceof ParsedFloat parsedFloat) {
      return resolveFloatMember(parsedMessageType, parsedFloat, messageContext, context);
    }
    if (member instanceof ParsedScaledInt parsedScaledInt) {
      return resolveScaledIntMember(parsedMessageType, parsedScaledInt, messageContext, context);
    }
    if (member instanceof ParsedArray parsedArray) {
      return resolveArrayMember(parsedMessageType, parsedArray, messageContext, context);
    }
    if (member instanceof ParsedVector parsedVector) {
      return resolveVectorMember(parsedMessageType, parsedVector, messageContext, context);
    }
    if (member instanceof ParsedBlobArray parsedBlobArray) {
      return resolveBlobArrayMember(parsedMessageType, parsedBlobArray, messageContext, context);
    }
    if (member instanceof ParsedBlobVector parsedBlobVector) {
      return resolveBlobVectorMember(parsedMessageType, parsedBlobVector, messageContext, context);
    }
    if (member instanceof ParsedVarString parsedVarString) {
      return resolveVarStringMember(parsedMessageType, parsedVarString, messageContext, context);
    }
    if (member instanceof ParsedChecksum parsedChecksum) {
      return resolveChecksumMember(parsedChecksum);
    }
    if (member instanceof ParsedPad parsedPad) {
      return resolvePadMember(parsedPad);
    }
    if (member instanceof ParsedIfBlock parsedIfBlock) {
      return resolveIfBlockMember(
          parsedMessageType, currentNamespace, parsedIfBlock, messageContext, context);
    }
    return resolveNestedTypeMember(
        parsedMessageType, currentNamespace, (ParsedMessageType) member, messageContext, context);
  }

  /**
   * Builds one primitive-field lookup map for a parsed message member list.
   *
   * <p>This lookup is used by {@code if} condition validation.
   *
   * @param members parsed members to scan
   * @return primitive field lookup map keyed by field name
   */
  private static Map<String, PrimitiveType> collectPrimitiveFieldTypes(
      List<ParsedMessageMember> members) {
    Map<String, PrimitiveType> primitiveFieldByName = new LinkedHashMap<>();
    collectPrimitiveFieldTypes(primitiveFieldByName, members);
    return Map.copyOf(primitiveFieldByName);
  }

  /**
   * Recursively collects primitive scalar field types from one member list.
   *
   * @param primitiveFieldByName destination lookup map
   * @param members parsed members to scan
   */
  private static void collectPrimitiveFieldTypes(
      Map<String, PrimitiveType> primitiveFieldByName, List<ParsedMessageMember> members) {
    for (ParsedMessageMember member : members) {
      if (member instanceof ParsedField parsedField) {
        PrimitiveType primitiveType = PrimitiveType.fromSchemaName(parsedField.typeName());
        if (primitiveType != null) {
          primitiveFieldByName.putIfAbsent(parsedField.name(), primitiveType);
        }
      }
      if (member instanceof ParsedIfBlock parsedIfBlock) {
        collectPrimitiveFieldTypes(primitiveFieldByName, parsedIfBlock.members());
      }
      if (member instanceof ParsedMessageType parsedNestedType) {
        collectPrimitiveFieldTypes(primitiveFieldByName, parsedNestedType.members());
      }
    }
  }

  /**
   * Resolves one parsed scalar field member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedField parsed field definition
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved field member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveFieldMember(
      ParsedMessageType parsedMessageType,
      ParsedField parsedField,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedField);
    SemanticValidationRules.validateIdentifier(
        parsedField.name(),
        "SEMANTIC_INVALID_FIELD_NAME",
        "Field name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    if (!messageContext.namedMembers.add(parsedField.name())) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_FIELD_NAME",
              "Duplicate member name in message "
                  + parsedMessageType.name()
                  + ": "
                  + parsedField.name(),
              sourcePath));
    }

    ResolvedTypeRef typeRef =
        resolveTypeRef(parsedField.typeName(), parsedMessageType.name(), parsedField, context);
    if (typeRef == null) {
      return null;
    }
    if (typeRef instanceof PrimitiveTypeRef) {
      messageContext.previousPrimitiveFieldNames.add(parsedField.name());
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved bitfield member
   */
  private static ResolvedMessageMember resolveBitFieldMember(
      ParsedMessageType parsedMessageType,
      ParsedBitField parsedBitField,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedBitField);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedBitField.name(),
        messageContext.namedMembers,
        sourcePath,
        context);
    return BitFieldResolver.resolveBitField(parsedBitField, sourcePath, context.diagnostics);
  }

  /**
   * Resolves one parsed float message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedFloat parsed float definition
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved float member
   */
  private static ResolvedMessageMember resolveFloatMember(
      ParsedMessageType parsedMessageType,
      ParsedFloat parsedFloat,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedFloat);
    SemanticValidationRules.validateIdentifier(
        parsedFloat.name(),
        "SEMANTIC_INVALID_FLOAT_NAME",
        "Float name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedFloat.name(),
        messageContext.namedMembers,
        sourcePath,
        context);
    SemanticValidationRules.validateFloatScaleRules(parsedFloat, sourcePath, context.diagnostics);

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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved scaled-int member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveScaledIntMember(
      ParsedMessageType parsedMessageType,
      ParsedScaledInt parsedScaledInt,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedScaledInt);
    SemanticValidationRules.validateIdentifier(
        parsedScaledInt.name(),
        "SEMANTIC_INVALID_SCALED_INT_NAME",
        "ScaledInt name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedScaledInt.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    PrimitiveType baseType = PrimitiveType.fromSchemaName(parsedScaledInt.baseTypeName());
    if (baseType == null) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE",
              "Invalid scaledInt baseType in message "
                  + parsedMessageType.name()
                  + ": "
                  + parsedScaledInt.baseTypeName(),
              sourcePath));
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved array member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveArrayMember(
      ParsedMessageType parsedMessageType,
      ParsedArray parsedArray,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedArray);
    SemanticValidationRules.validateIdentifier(
        parsedArray.name(),
        "SEMANTIC_INVALID_ARRAY_NAME",
        "Array name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedArray.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    ResolvedTypeRef elementTypeRef =
        resolveTypeRef(
            parsedArray.elementTypeName(),
            "array " + parsedArray.name() + " in message " + parsedMessageType.name(),
            parsedArray,
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved vector member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveVectorMember(
      ParsedMessageType parsedMessageType,
      ParsedVector parsedVector,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedVector);
    SemanticValidationRules.validateIdentifier(
        parsedVector.name(),
        "SEMANTIC_INVALID_VECTOR_NAME",
        "Vector name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedVector.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    ResolvedTypeRef elementTypeRef =
        resolveTypeRef(
            parsedVector.elementTypeName(),
            "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
            parsedVector,
            context);
    ResolvedLengthMode lengthMode =
        LengthModeResolver.resolveLengthMode(
            parsedVector.lengthMode(),
            "vector " + parsedVector.name() + " in message " + parsedMessageType.name(),
            sourcePath,
            context.diagnostics,
            messageContext.previousPrimitiveFieldNames,
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved blob-array member
   */
  private static ResolvedMessageMember resolveBlobArrayMember(
      ParsedMessageType parsedMessageType,
      ParsedBlobArray parsedBlobArray,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedBlobArray);
    SemanticValidationRules.validateIdentifier(
        parsedBlobArray.name(),
        "SEMANTIC_INVALID_BLOB_ARRAY_NAME",
        "blobArray name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedBlobArray.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    return new ResolvedBlobArray(
        parsedBlobArray.name(), parsedBlobArray.length(), parsedBlobArray.comment());
  }

  /**
   * Resolves one parsed blob-vector message member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param parsedBlobVector parsed blob-vector definition
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved blob-vector member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveBlobVectorMember(
      ParsedMessageType parsedMessageType,
      ParsedBlobVector parsedBlobVector,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedBlobVector);
    SemanticValidationRules.validateIdentifier(
        parsedBlobVector.name(),
        "SEMANTIC_INVALID_BLOB_VECTOR_NAME",
        "blobVector name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedBlobVector.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    ResolvedLengthMode lengthMode =
        LengthModeResolver.resolveLengthMode(
            parsedBlobVector.lengthMode(),
            "blobVector " + parsedBlobVector.name() + " in message " + parsedMessageType.name(),
            sourcePath,
            context.diagnostics,
            messageContext.previousPrimitiveFieldNames,
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved varString member, or {@code null} when invalid
   */
  private static ResolvedMessageMember resolveVarStringMember(
      ParsedMessageType parsedMessageType,
      ParsedVarString parsedVarString,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedVarString);
    SemanticValidationRules.validateIdentifier(
        parsedVarString.name(),
        "SEMANTIC_INVALID_VAR_STRING_NAME",
        "varString name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        parsedVarString.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    ResolvedLengthMode lengthMode =
        LengthModeResolver.resolveLengthMode(
            parsedVarString.lengthMode(),
            "varString " + parsedVarString.name() + " in message " + parsedMessageType.name(),
            sourcePath,
            context.diagnostics,
            messageContext.previousPrimitiveFieldNames,
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
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved conditional block
   */
  private static ResolvedMessageMember resolveIfBlockMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedIfBlock parsedIfBlock,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(parsedIfBlock);
    ResolvedIfCondition resolvedCondition = null;
    if (parsedIfBlock.test().isBlank()) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_IF_TEST",
              "if@test must not be blank in message " + parsedMessageType.name() + ".",
              sourcePath));
    } else {
      resolvedCondition =
          ConditionSemanticValidator.resolveIfCondition(
              parsedMessageType.name(),
              parsedIfBlock.test(),
              messageContext.primitiveFieldByName,
              sourcePath,
              context.diagnostics);
    }
    if (parsedIfBlock.members().isEmpty()) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_EMPTY_IF_BLOCK",
              "if block in message " + parsedMessageType.name() + " must contain members.",
              sourcePath));
    }

    MessageResolutionContext ifContext =
        new MessageResolutionContext(
            messageContext.previousPrimitiveFieldNames, messageContext.primitiveFieldByName);
    List<ResolvedMessageMember> resolvedMembers = new ArrayList<>();
    for (ParsedMessageMember nestedMember : parsedIfBlock.members()) {
      ResolvedMessageMember resolvedMember =
          resolveMessageMember(
              parsedMessageType, currentNamespace, nestedMember, ifContext, context);
      if (resolvedMember != null) {
        resolvedMembers.add(resolvedMember);
      }
    }
    if (resolvedCondition == null) {
      return null;
    }
    return new ResolvedIfBlock(resolvedCondition, resolvedMembers);
  }

  /**
   * Resolves one nested parsed {@code type} member.
   *
   * @param parsedMessageType parsed parent message definition
   * @param currentNamespace effective namespace currently active for this message
   * @param nestedType parsed nested message definition
   * @param messageContext per-message resolution state
   * @param context shared semantic-resolution state
   * @return resolved nested message member
   */
  private static ResolvedMessageMember resolveNestedTypeMember(
      ParsedMessageType parsedMessageType,
      String currentNamespace,
      ParsedMessageType nestedType,
      MessageResolutionContext messageContext,
      ResolutionContext context) {
    String sourcePath = context.sourcePathFor(nestedType);
    SemanticValidationRules.validateIdentifier(
        nestedType.name(),
        "SEMANTIC_INVALID_NESTED_TYPE_NAME",
        "Nested type name must be a valid identifier in message " + parsedMessageType.name() + ": ",
        sourcePath,
        context.diagnostics);
    registerDuplicateMemberName(
        parsedMessageType.name(),
        nestedType.name(),
        messageContext.namedMembers,
        sourcePath,
        context);

    if (nestedType.namespaceOverride() != null) {
      SemanticValidationRules.validateNamespace(
          nestedType.namespaceOverride(), "type@namespace", sourcePath, context.diagnostics);
    }
    if (nestedType.members().isEmpty()) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_EMPTY_NESTED_TYPE",
              "Nested type "
                  + nestedType.name()
                  + " in message "
                  + parsedMessageType.name()
                  + " must contain members.",
              sourcePath));
    }

    return resolveMessageType(currentNamespace, nestedType, context);
  }

  /**
   * Adds a duplicate-member diagnostic when a message member name is repeated.
   *
   * @param messageTypeName parent message name
   * @param memberName member name being registered
   * @param knownMemberNames previously seen names in this message
   * @param sourcePath source path for the member being registered
   * @param context shared semantic-resolution state
   */
  private static void registerDuplicateMemberName(
      String messageTypeName,
      String memberName,
      Set<String> knownMemberNames,
      String sourcePath,
      ResolutionContext context) {
    if (!knownMemberNames.add(memberName)) {
      context.diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_MEMBER_NAME",
              "Duplicate member name in message " + messageTypeName + ": " + memberName,
              sourcePath));
    }
  }
}
