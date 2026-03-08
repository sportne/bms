package io.github.sportne.bms.codegen.java;

import io.github.sportne.bms.codegen.common.SupportValidationRules;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
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
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Provides detailed Java backend support checks for resolved members and type references.
 *
 * <p>{@link JavaSupportChecker} owns orchestration. This class owns per-member and per-type-ref
 * validation logic.
 */
final class JavaMemberSupportChecker {
  /** Prevents instantiation of this static utility class. */
  private JavaMemberSupportChecker() {}

  /**
   * Checks support for one message member.
   *
   * @param member message member to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  static void checkMemberSupport(
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (member instanceof ResolvedField resolvedField) {
      checkFieldTypeSupport(
          resolvedField,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBitField) {
      return;
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      checkScaledIntSupport(resolvedScaledInt, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedArray resolvedArray) {
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedVector resolvedVector) {
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedBlobArray) {
      return;
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      checkBlobVectorSupport(
          resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      checkVarStringSupport(
          resolvedVarString, messageType, primitiveFieldByName, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedPad) {
      return;
    }
    if (member instanceof ResolvedChecksum resolvedChecksum) {
      checkChecksumSupport(resolvedChecksum, messageType, outputPath, diagnostics);
      return;
    }
    if (member instanceof ResolvedIfBlock resolvedIfBlock) {
      checkIfBlockSupport(
          resolvedIfBlock,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }
    if (member instanceof ResolvedMessageType resolvedNestedMessageType) {
      checkNestedTypeSupport(
          resolvedNestedMessageType,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(), member.getClass().getSimpleName(), outputPath.toString()));
  }

  /**
   * Checks support for checksum members.
   *
   * @param resolvedChecksum checksum definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkChecksumSupport(
      ResolvedChecksum resolvedChecksum,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!SupportValidationRules.isSupportedChecksumAlgorithm(resolvedChecksum.algorithm())) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(unsupported algorithm)",
              outputPath.toString()));
      return;
    }

    if (!SupportValidationRules.hasValidChecksumRange(resolvedChecksum.range())) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(invalid range)",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for conditional blocks and recursively validates contained members.
   *
   * @param resolvedIfBlock conditional block to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkIfBlockSupport(
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    for (ResolvedMessageMember nestedMember : resolvedIfBlock.members()) {
      checkMemberSupport(
          nestedMember,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
    }
  }

  /**
   * Checks support for nested message definitions and recursively validates contained members.
   *
   * @param resolvedNestedType nested message definition
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkNestedTypeSupport(
      ResolvedMessageType resolvedNestedType,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    for (ResolvedMessageMember nestedMember : resolvedNestedType.members()) {
      checkMemberSupport(
          nestedMember,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
    }
  }

  /**
   * Checks support for a resolved field member and its referenced type.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFieldTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    if (typeRef instanceof PrimitiveTypeRef || typeRef instanceof MessageTypeRef) {
      return;
    }

    if (typeRef instanceof FloatTypeRef floatTypeRef) {
      ResolvedFloat resolvedFloat =
          generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
      if (resolvedFloat == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable float was not found: " + floatTypeRef.floatTypeName()));
        return;
      }
      checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      ResolvedScaledInt resolvedScaledInt =
          generationContext.reusableScaledIntByName().get(scaledIntTypeRef.scaledIntTypeName());
      if (resolvedScaledInt == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable scaledInt was not found: " + scaledIntTypeRef.scaledIntTypeName()));
        return;
      }
      checkScaledIntSupport(resolvedScaledInt, messageType, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof ArrayTypeRef arrayTypeRef) {
      ResolvedArray resolvedArray =
          generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
      if (resolvedArray == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable array was not found: " + arrayTypeRef.arrayTypeName()));
        return;
      }
      checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
      return;
    }

    if (typeRef instanceof VectorTypeRef vectorTypeRef) {
      ResolvedVector resolvedVector =
          generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
      if (resolvedVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable vector was not found: " + vectorTypeRef.vectorTypeName()));
        return;
      }
      checkVectorSupport(
          resolvedVector,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }

    if (typeRef instanceof BlobArrayTypeRef
        || typeRef instanceof BlobVectorTypeRef
        || typeRef instanceof VarStringTypeRef) {
      checkBlobOrVarStringTypeSupport(
          resolvedField,
          messageType,
          generationContext,
          primitiveFieldByName,
          typeRef,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedTypeRefDiagnostic(
            messageType.name(),
            resolvedField.name(),
            typeRef.getClass().getSimpleName(),
            outputPath.toString(),
            "This type reference is not implemented in the Java backend yet."));
  }

  /**
   * Checks support for blob and varString field type references.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param typeRef resolved type reference being checked
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobOrVarStringTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      ResolvedTypeRef typeRef,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (typeRef instanceof BlobArrayTypeRef blobArrayTypeRef) {
      if (!generationContext
          .reusableBlobArrayByName()
          .containsKey(blobArrayTypeRef.blobArrayTypeName())) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable blobArray was not found: " + blobArrayTypeRef.blobArrayTypeName()));
      }
      return;
    }

    if (typeRef instanceof BlobVectorTypeRef blobVectorTypeRef) {
      ResolvedBlobVector resolvedBlobVector =
          generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
      if (resolvedBlobVector == null) {
        diagnostics.add(
            unsupportedTypeRefDiagnostic(
                messageType.name(),
                resolvedField.name(),
                typeRef.getClass().getSimpleName(),
                outputPath.toString(),
                "Reusable blobVector was not found: " + blobVectorTypeRef.blobVectorTypeName()));
      } else {
        checkBlobVectorSupport(
            resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
      }
      return;
    }

    VarStringTypeRef varStringTypeRef = (VarStringTypeRef) typeRef;
    ResolvedVarString resolvedVarString =
        generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
    if (resolvedVarString == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath.toString(),
              "Reusable varString was not found: " + varStringTypeRef.varStringTypeName()));
      return;
    }
    checkVarStringSupport(
        resolvedVarString, messageType, primitiveFieldByName, outputPath, diagnostics);
  }

  /**
   * Checks support for one float definition.
   *
   * @param resolvedFloat float definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFloatSupport(
      ResolvedFloat resolvedFloat,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F16) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F32) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED && resolvedFloat.size() == FloatSize.F64) {
      return;
    }
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedFloat(" + resolvedFloat.encoding() + ", " + resolvedFloat.size() + ")",
            outputPath.toString()));
  }

  /**
   * Checks support for one scaled-int definition.
   *
   * @param resolvedScaledInt scaledInt definition to validate
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkScaledIntSupport(
      ResolvedScaledInt resolvedScaledInt,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    // Scaled integers are fully supported in this Java backend milestone.
  }

  /**
   * Checks support for one array definition.
   *
   * @param resolvedArray array definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkArraySupport(
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedArray.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedArray(elementType="
                  + resolvedArray.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for one vector definition.
   *
   * @param resolvedVector vector definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVectorSupport(
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      JavaCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedVector.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedVector(elementType="
                  + resolvedVector.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath.toString()));
      return;
    }

    checkLengthModeSupport(
        resolvedVector.lengthMode(),
        resolvedVector.elementTypeRef(),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "vector " + resolvedVector.name());
  }

  /**
   * Checks support for one blob-vector definition.
   *
   * @param resolvedBlobVector blob-vector definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobVectorSupport(
      ResolvedBlobVector resolvedBlobVector,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    checkLengthModeSupport(
        resolvedBlobVector.lengthMode(),
        new PrimitiveTypeRef(PrimitiveType.UINT8),
        messageType,
        primitiveFieldByName,
        outputPath,
        diagnostics,
        "blobVector " + resolvedBlobVector.name());
  }

  /**
   * Checks support for one varString definition.
   *
   * @param resolvedVarString varString definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVarStringSupport(
      ResolvedVarString resolvedVarString,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedVarString.lengthMode()
        instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                "varString "
                    + resolvedVarString.name()
                    + "(countField ref=\""
                    + resolvedCountFieldLength.ref()
                    + "\")",
                outputPath.toString()));
      }
      return;
    }

    if (resolvedVarString.lengthMode()
        instanceof ResolvedTerminatorValueLength resolvedTerminatorValueLength) {
      validateVarStringTerminatorLiteral(
          resolvedVarString,
          resolvedTerminatorValueLength.value(),
          messageType,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "varString " + resolvedVarString.name() + "(terminatorField path)",
            outputPath.toString()));
  }

  /**
   * Checks that one varString terminator literal is supported.
   *
   * @param resolvedVarString varString definition to validate
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void validateVarStringTerminatorLiteral(
      ResolvedVarString resolvedVarString,
      String literal,
      ResolvedMessageType messageType,
      Path outputPath,
      List<Diagnostic> diagnostics) {
    SupportValidationRules.LiteralValidation validation =
        SupportValidationRules.validateUint8Literal(literal);
    if (validation.status() == SupportValidationRules.LiteralValidationStatus.OUT_OF_RANGE) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "varString "
                  + resolvedVarString.name()
                  + "(terminator literal out of range: "
                  + validation.literal()
                  + ")",
              outputPath.toString()));
      return;
    }
    if (validation.status() == SupportValidationRules.LiteralValidationStatus.INVALID) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "varString "
                  + resolvedVarString.name()
                  + "(invalid terminator literal: "
                  + validation.literal()
                  + ")",
              outputPath.toString()));
    }
  }

  /**
   * Checks support for one length mode.
   *
   * @param lengthMode length mode to validate
   * @param elementTypeRef vector/blob element type
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path used in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName owner name used in diagnostic messages
   */
  private static void checkLengthModeSupport(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      Path outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    SupportValidationRules.LengthModeValidation validation =
        SupportValidationRules.validateLengthMode(lengthMode, elementTypeRef, primitiveFieldByName);
    if (validation.status() == SupportValidationRules.LengthModeValidationStatus.VALID) {
      return;
    }
    if (validation.status()
        == SupportValidationRules.LengthModeValidationStatus.COUNT_FIELD_MISSING) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(countField ref=\"" + validation.detail() + "\")",
              outputPath.toString()));
      return;
    }
    if (validation.status()
        == SupportValidationRules.LengthModeValidationStatus.TERMINATOR_PATH_MISSING_MATCH) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminatorField path missing terminatorMatch)",
              outputPath.toString()));
      return;
    }
    if (validation.status()
        == SupportValidationRules.LengthModeValidationStatus.TERMINATOR_REQUIRES_PRIMITIVE) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminator modes require primitive element types)",
              outputPath.toString()));
      return;
    }
    if (validation.status()
        == SupportValidationRules.LengthModeValidationStatus.TERMINATOR_LITERAL_OUT_OF_RANGE) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminator literal out of range: " + validation.detail() + ")",
              outputPath.toString()));
      return;
    }
    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            ownerName + "(invalid terminator literal: " + validation.detail() + ")",
            outputPath.toString()));
  }

  /**
   * Determines whether a collection element type is supported in this backend milestone.
   *
   * @param elementTypeRef element type reference to check
   * @param generationContext reusable lookup maps
   * @return {@code true} when the element type can be emitted
   */
  private static boolean isCollectionElementTypeSupported(
      ResolvedTypeRef elementTypeRef, JavaCodeGenerator.GenerationContext generationContext) {
    if (elementTypeRef instanceof PrimitiveTypeRef || elementTypeRef instanceof MessageTypeRef) {
      return true;
    }

    if (elementTypeRef instanceof FloatTypeRef floatTypeRef) {
      return generationContext.reusableFloatByName().containsKey(floatTypeRef.floatTypeName());
    }

    if (elementTypeRef instanceof ScaledIntTypeRef scaledIntTypeRef) {
      return generationContext
          .reusableScaledIntByName()
          .containsKey(scaledIntTypeRef.scaledIntTypeName());
    }

    return false;
  }

  /**
   * Creates one unsupported-member diagnostic.
   *
   * @param messageName parent message name
   * @param memberLabel unsupported member label
   * @param outputPath output path shown in diagnostics
   * @return unsupported-member diagnostic
   */
  static Diagnostic unsupportedMemberDiagnostic(
      String messageName, String memberLabel, String outputPath) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_JAVA_UNSUPPORTED_MEMBER",
        "Java generator does not support message member "
            + memberLabel
            + " in message "
            + messageName
            + " yet.",
        outputPath,
        -1,
        -1);
  }

  /**
   * Creates one unsupported-type-reference diagnostic.
   *
   * @param messageName parent message name
   * @param fieldName field name that owns the reference
   * @param referenceKind reference kind label
   * @param outputPath output path shown in diagnostics
   * @param reason additional reason text
   * @return unsupported-type-reference diagnostic
   */
  private static Diagnostic unsupportedTypeRefDiagnostic(
      String messageName,
      String fieldName,
      String referenceKind,
      String outputPath,
      String reason) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_JAVA_UNSUPPORTED_TYPE_REF",
        "Java generator does not support field type reference "
            + referenceKind
            + " for field "
            + fieldName
            + " in message "
            + messageName
            + " yet. "
            + reason,
        outputPath,
        -1,
        -1);
  }
}
