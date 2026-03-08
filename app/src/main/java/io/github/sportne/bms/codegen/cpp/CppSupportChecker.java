package io.github.sportne.bms.codegen.cpp;

import io.github.sportne.bms.codegen.common.ChecksumRangeRules;
import io.github.sportne.bms.codegen.common.LengthModeRules;
import io.github.sportne.bms.codegen.common.PrimitiveFieldIndex;
import io.github.sportne.bms.codegen.common.PrimitiveNumericRules;
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
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks whether one resolved message can be emitted by the C++ backend.
 *
 * <p>This class isolates support-check logic from source rendering.
 */
final class CppSupportChecker {
  /** Prevents instantiation of this static utility class. */
  private CppSupportChecker() {}

  /**
   * Verifies that this backend supports every member and type in the message.
   *
   * @param messageType message being generated
   * @param generationContext reusable lookup maps
   * @param sourcePath output source path used in diagnostics
   * @throws BmsException if unsupported members or type references are found
   */
  static void ensureSupportedMembers(
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Path sourcePath)
      throws BmsException {
    List<Diagnostic> diagnostics = new ArrayList<>();
    Map<String, PrimitiveType> primitiveFieldByName = primitiveFieldsByName(messageType);
    for (ResolvedMessageMember member : messageType.members()) {
      checkMemberSupport(
          member,
          messageType,
          generationContext,
          primitiveFieldByName,
          sourcePath.toString(),
          diagnostics);
    }
    checkFlattenedMemberNames(
        messageType.name(),
        messageType.members(),
        new TreeSet<>(),
        sourcePath.toString(),
        diagnostics,
        "message");

    if (!diagnostics.isEmpty()) {
      throw new BmsException("C++ code generation failed due to unsupported members.", diagnostics);
    }
  }

  /**
   * Builds a lookup map from primitive field name to primitive type for one message.
   *
   * @param messageType message being generated
   * @return immutable primitive field lookup map
   */
  private static Map<String, PrimitiveType> primitiveFieldsByName(ResolvedMessageType messageType) {
    return PrimitiveFieldIndex.collect(messageType);
  }

  /**
   * Checks support for one message member.
   *
   * @param member member to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkMemberSupport(
      ResolvedMessageMember member,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
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
    if (member instanceof ResolvedScaledInt) {
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
    if (member instanceof ResolvedMessageType resolvedNestedType) {
      checkNestedTypeSupport(
          resolvedNestedType,
          messageType,
          generationContext,
          primitiveFieldByName,
          outputPath,
          diagnostics);
      return;
    }

    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(), member.getClass().getSimpleName(), outputPath));
  }

  /**
   * Checks support for one field member and its referenced type.
   *
   * @param resolvedField field to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFieldTypeSupport(
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    ResolvedTypeRef typeRef = resolvedField.typeRef();
    if (typeRef instanceof PrimitiveTypeRef || typeRef instanceof MessageTypeRef) {
      return;
    }
    if (checkFloatTypeRefSupport(
        typeRef, resolvedField, messageType, generationContext, outputPath, diagnostics)) {
      return;
    }
    if (checkScaledIntTypeRefSupport(
        typeRef, resolvedField, messageType, generationContext, outputPath, diagnostics)) {
      return;
    }
    if (checkArrayTypeRefSupport(
        typeRef, resolvedField, messageType, generationContext, outputPath, diagnostics)) {
      return;
    }
    if (checkVectorTypeRefSupport(
        typeRef,
        resolvedField,
        messageType,
        generationContext,
        primitiveFieldByName,
        outputPath,
        diagnostics)) {
      return;
    }
    if (checkBlobArrayTypeRefSupport(
        typeRef, resolvedField, messageType, generationContext, outputPath, diagnostics)) {
      return;
    }
    if (checkBlobVectorTypeRefSupport(
        typeRef,
        resolvedField,
        messageType,
        generationContext,
        primitiveFieldByName,
        outputPath,
        diagnostics)) {
      return;
    }
    if (checkVarStringTypeRefSupport(
        typeRef,
        resolvedField,
        messageType,
        generationContext,
        primitiveFieldByName,
        outputPath,
        diagnostics)) {
      return;
    }

    diagnostics.add(
        unsupportedTypeRefDiagnostic(
            messageType.name(),
            resolvedField.name(),
            typeRef.getClass().getSimpleName(),
            outputPath,
            "This type reference is not implemented in the C++ backend yet."));
  }

  /**
   * Checks support for one reusable float reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkFloatTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof FloatTypeRef floatTypeRef)) {
      return false;
    }
    ResolvedFloat resolvedFloat =
        generationContext.reusableFloatByName().get(floatTypeRef.floatTypeName());
    if (resolvedFloat == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable float was not found: " + floatTypeRef.floatTypeName()));
      return true;
    }
    checkFloatSupport(resolvedFloat, messageType, outputPath, diagnostics);
    return true;
  }

  /**
   * Checks support for one reusable scaled-int reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkScaledIntTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof ScaledIntTypeRef scaledIntTypeRef)) {
      return false;
    }
    if (!generationContext
        .reusableScaledIntByName()
        .containsKey(scaledIntTypeRef.scaledIntTypeName())) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable scaledInt was not found: " + scaledIntTypeRef.scaledIntTypeName()));
    }
    return true;
  }

  /**
   * Checks support for one reusable array reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkArrayTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof ArrayTypeRef arrayTypeRef)) {
      return false;
    }
    ResolvedArray resolvedArray =
        generationContext.reusableArrayByName().get(arrayTypeRef.arrayTypeName());
    if (resolvedArray == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable array was not found: " + arrayTypeRef.arrayTypeName()));
      return true;
    }
    checkArraySupport(resolvedArray, messageType, generationContext, outputPath, diagnostics);
    return true;
  }

  /**
   * Checks support for one reusable vector reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkVectorTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof VectorTypeRef vectorTypeRef)) {
      return false;
    }
    ResolvedVector resolvedVector =
        generationContext.reusableVectorByName().get(vectorTypeRef.vectorTypeName());
    if (resolvedVector == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable vector was not found: " + vectorTypeRef.vectorTypeName()));
      return true;
    }
    checkVectorSupport(
        resolvedVector,
        messageType,
        generationContext,
        primitiveFieldByName,
        outputPath,
        diagnostics);
    return true;
  }

  /**
   * Checks support for one reusable blob-array reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkBlobArrayTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof BlobArrayTypeRef blobArrayTypeRef)) {
      return false;
    }
    if (!generationContext
        .reusableBlobArrayByName()
        .containsKey(blobArrayTypeRef.blobArrayTypeName())) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable blobArray was not found: " + blobArrayTypeRef.blobArrayTypeName()));
    }
    return true;
  }

  /**
   * Checks support for one reusable blob-vector reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkBlobVectorTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof BlobVectorTypeRef blobVectorTypeRef)) {
      return false;
    }
    ResolvedBlobVector resolvedBlobVector =
        generationContext.reusableBlobVectorByName().get(blobVectorTypeRef.blobVectorTypeName());
    if (resolvedBlobVector == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable blobVector was not found: " + blobVectorTypeRef.blobVectorTypeName()));
      return true;
    }
    checkBlobVectorSupport(
        resolvedBlobVector, messageType, primitiveFieldByName, outputPath, diagnostics);
    return true;
  }

  /**
   * Checks support for one reusable varString reference.
   *
   * @param typeRef field type reference
   * @param resolvedField field that owns the type reference
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @return {@code true} when this helper handled the type reference
   */
  private static boolean checkVarStringTypeRefSupport(
      ResolvedTypeRef typeRef,
      ResolvedField resolvedField,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!(typeRef instanceof VarStringTypeRef varStringTypeRef)) {
      return false;
    }
    ResolvedVarString resolvedVarString =
        generationContext.reusableVarStringByName().get(varStringTypeRef.varStringTypeName());
    if (resolvedVarString == null) {
      diagnostics.add(
          unsupportedTypeRefDiagnostic(
              messageType.name(),
              resolvedField.name(),
              typeRef.getClass().getSimpleName(),
              outputPath,
              "Reusable varString was not found: " + varStringTypeRef.varStringTypeName()));
      return true;
    }
    checkVarStringSupport(
        resolvedVarString, messageType, primitiveFieldByName, outputPath, diagnostics);
    return true;
  }

  /**
   * Checks support for one float definition.
   *
   * @param resolvedFloat float definition to validate
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkFloatSupport(
      ResolvedFloat resolvedFloat,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (resolvedFloat.encoding() == FloatEncoding.IEEE754) {
      if (resolvedFloat.size() == FloatSize.F16
          || resolvedFloat.size() == FloatSize.F32
          || resolvedFloat.size() == FloatSize.F64) {
        return;
      }
    }
    if (resolvedFloat.encoding() == FloatEncoding.SCALED) {
      if (resolvedFloat.size() == FloatSize.F16
          || resolvedFloat.size() == FloatSize.F32
          || resolvedFloat.size() == FloatSize.F64) {
        return;
      }
    }
    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedFloat(" + resolvedFloat.encoding() + ", " + resolvedFloat.size() + ")",
            outputPath));
  }

  /**
   * Checks support for one array definition.
   *
   * @param resolvedArray array definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkArraySupport(
      ResolvedArray resolvedArray,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (isCollectionElementTypeSupported(resolvedArray.elementTypeRef(), generationContext)) {
      return;
    }
    diagnostics.add(
        unsupportedMemberDiagnostic(
            messageType.name(),
            "ResolvedArray(elementType="
                + resolvedArray.elementTypeRef().getClass().getSimpleName()
                + ")",
            outputPath));
  }

  /**
   * Checks support for one vector definition.
   *
   * @param resolvedVector vector definition to validate
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVectorSupport(
      ResolvedVector resolvedVector,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!isCollectionElementTypeSupported(resolvedVector.elementTypeRef(), generationContext)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "ResolvedVector(elementType="
                  + resolvedVector.elementTypeRef().getClass().getSimpleName()
                  + ")",
              outputPath));
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
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkBlobVectorSupport(
      ResolvedBlobVector resolvedBlobVector,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
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
   * Checks support for one checksum definition.
   *
   * @param resolvedChecksum checksum definition to validate
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkChecksumSupport(
      ResolvedChecksum resolvedChecksum,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics) {
    if (!isSupportedChecksumAlgorithm(resolvedChecksum.algorithm())) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(unsupported algorithm)",
              outputPath));
      return;
    }
    if (ChecksumRangeRules.parse(resolvedChecksum.range()) == null) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "checksum " + resolvedChecksum.algorithm() + "(invalid range)",
              outputPath));
    }
  }

  /**
   * Checks support for one conditional block and all nested members.
   *
   * @param resolvedIfBlock resolved conditional block
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkIfBlockSupport(
      ResolvedIfBlock resolvedIfBlock,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
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
   * Checks support for one nested message block and all nested members.
   *
   * @param resolvedNestedType resolved nested message block
   * @param messageType parent message type
   * @param generationContext reusable lookup maps
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkNestedTypeSupport(
      ResolvedMessageType resolvedNestedType,
      ResolvedMessageType messageType,
      CppCodeGenerator.GenerationContext generationContext,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
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
   * Rejects flattened member-name collisions that would produce duplicate C++ fields.
   *
   * @param ownerName parent message name used in diagnostics
   * @param members members to inspect in declaration order
   * @param flattenedMemberNames destination set of flattened declaration names
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerContext short owner label used in diagnostics
   */
  private static void checkFlattenedMemberNames(
      String ownerName,
      List<ResolvedMessageMember> members,
      Set<String> flattenedMemberNames,
      String outputPath,
      List<Diagnostic> diagnostics,
      String ownerContext) {
    for (ResolvedMessageMember member : members) {
      if (member instanceof ResolvedIfBlock resolvedIfBlock) {
        checkFlattenedMemberNames(
            ownerName,
            resolvedIfBlock.members(),
            flattenedMemberNames,
            outputPath,
            diagnostics,
            "if block");
        continue;
      }
      if (member instanceof ResolvedMessageType resolvedNestedType) {
        checkFlattenedMemberNames(
            ownerName,
            resolvedNestedType.members(),
            flattenedMemberNames,
            outputPath,
            diagnostics,
            "nested type " + resolvedNestedType.name());
        continue;
      }
      if (!isDeclarableMember(member)) {
        continue;
      }
      String flattenedName = memberName(member);
      if (!flattenedMemberNames.add(flattenedName)) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                ownerName,
                ownerContext + " member name collision for " + flattenedName,
                outputPath));
      }
    }
  }

  /**
   * Returns whether one member kind produces a generated C++ field declaration.
   *
   * @param member member to inspect
   * @return {@code true} when the member is emitted as a C++ field
   */
  private static boolean isDeclarableMember(ResolvedMessageMember member) {
    return member instanceof ResolvedField
        || member instanceof ResolvedBitField
        || member instanceof ResolvedFloat
        || member instanceof ResolvedScaledInt
        || member instanceof ResolvedArray
        || member instanceof ResolvedVector
        || member instanceof ResolvedBlobArray
        || member instanceof ResolvedBlobVector
        || member instanceof ResolvedVarString;
  }

  /**
   * Checks support for one varString definition.
   *
   * @param resolvedVarString varString definition to validate
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void checkVarStringSupport(
      ResolvedVarString resolvedVarString,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
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
                outputPath));
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
            outputPath));
  }

  /**
   * Checks that one varString terminator literal is valid.
   *
   * @param resolvedVarString varString definition to validate
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   */
  private static void validateVarStringTerminatorLiteral(
      ResolvedVarString resolvedVarString,
      String literal,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics) {
    try {
      BigInteger value = PrimitiveNumericRules.parseNumericLiteral(literal);
      if (!PrimitiveNumericRules.fitsPrimitiveRange(value, PrimitiveType.UINT8)) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                "varString "
                    + resolvedVarString.name()
                    + "(terminator literal out of range: "
                    + literal
                    + ")",
                outputPath));
      }
    } catch (NumberFormatException exception) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              "varString "
                  + resolvedVarString.name()
                  + "(invalid terminator literal: "
                  + literal
                  + ")",
              outputPath));
    }
  }

  /**
   * Returns whether one checksum algorithm is supported by this C++ backend.
   *
   * @param algorithm checksum algorithm literal
   * @return {@code true} when supported
   */
  private static boolean isSupportedChecksumAlgorithm(String algorithm) {
    return "crc16".equals(algorithm)
        || "crc32".equals(algorithm)
        || "crc64".equals(algorithm)
        || "sha256".equals(algorithm);
  }

  /**
   * Checks support for one vector/blob-vector length mode.
   *
   * @param lengthMode resolved length mode
   * @param elementTypeRef element type reference
   * @param messageType parent message type
   * @param primitiveFieldByName primitive field lookup map
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName member name used in diagnostics
   */
  private static void checkLengthModeSupport(
      ResolvedLengthMode lengthMode,
      ResolvedTypeRef elementTypeRef,
      ResolvedMessageType messageType,
      Map<String, PrimitiveType> primitiveFieldByName,
      String outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (lengthMode instanceof ResolvedCountFieldLength resolvedCountFieldLength) {
      if (!primitiveFieldByName.containsKey(resolvedCountFieldLength.ref())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(countField ref=\"" + resolvedCountFieldLength.ref() + "\")",
                outputPath));
      }
      return;
    }

    String literal = LengthModeRules.terminatorLiteral(lengthMode);
    if (literal == null) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminatorField path missing terminatorMatch)",
              outputPath));
      return;
    }
    validatePrimitiveTerminatorLiteral(
        elementTypeRef, literal, messageType, outputPath, diagnostics, ownerName);
  }

  /**
   * Checks that one terminator literal is valid for a primitive element type.
   *
   * @param elementTypeRef element type of vector/blob vector
   * @param literal terminator literal text
   * @param messageType parent message type
   * @param outputPath output path shown in diagnostics
   * @param diagnostics destination diagnostics list
   * @param ownerName member name used in diagnostics
   */
  private static void validatePrimitiveTerminatorLiteral(
      ResolvedTypeRef elementTypeRef,
      String literal,
      ResolvedMessageType messageType,
      String outputPath,
      List<Diagnostic> diagnostics,
      String ownerName) {
    if (!(elementTypeRef instanceof PrimitiveTypeRef primitiveTypeRef)) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(terminator modes require primitive element types)",
              outputPath));
      return;
    }
    try {
      BigInteger value = PrimitiveNumericRules.parseNumericLiteral(literal);
      if (!PrimitiveNumericRules.fitsPrimitiveRange(value, primitiveTypeRef.primitiveType())) {
        diagnostics.add(
            unsupportedMemberDiagnostic(
                messageType.name(),
                ownerName + "(terminator literal out of range: " + literal + ")",
                outputPath));
      }
    } catch (NumberFormatException exception) {
      diagnostics.add(
          unsupportedMemberDiagnostic(
              messageType.name(),
              ownerName + "(invalid terminator literal: " + literal + ")",
              outputPath));
    }
  }

  /**
   * Returns whether one collection element type is supported in C++ milestone 03.
   *
   * @param elementTypeRef element type reference to inspect
   * @param generationContext reusable lookup maps
   * @return {@code true} when supported
   */
  private static boolean isCollectionElementTypeSupported(
      ResolvedTypeRef elementTypeRef, CppCodeGenerator.GenerationContext generationContext) {
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
  private static Diagnostic unsupportedMemberDiagnostic(
      String messageName, String memberLabel, String outputPath) {
    return new Diagnostic(
        DiagnosticSeverity.ERROR,
        "GENERATOR_CPP_UNSUPPORTED_MEMBER",
        "C++ generator does not support message member "
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
        "GENERATOR_CPP_UNSUPPORTED_TYPE_REF",
        "C++ generator does not support field type reference "
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

  /**
   * Resolves one member name.
   *
   * @param member member to inspect
   * @return member name
   */
  private static String memberName(ResolvedMessageMember member) {
    if (member instanceof ResolvedField resolvedField) {
      return resolvedField.name();
    }
    if (member instanceof ResolvedBitField resolvedBitField) {
      return resolvedBitField.name();
    }
    if (member instanceof ResolvedFloat resolvedFloat) {
      return resolvedFloat.name();
    }
    if (member instanceof ResolvedScaledInt resolvedScaledInt) {
      return resolvedScaledInt.name();
    }
    if (member instanceof ResolvedArray resolvedArray) {
      return resolvedArray.name();
    }
    if (member instanceof ResolvedVector resolvedVector) {
      return resolvedVector.name();
    }
    if (member instanceof ResolvedBlobArray resolvedBlobArray) {
      return resolvedBlobArray.name();
    }
    if (member instanceof ResolvedBlobVector resolvedBlobVector) {
      return resolvedBlobVector.name();
    }
    if (member instanceof ResolvedVarString resolvedVarString) {
      return resolvedVarString.name();
    }
    throw new IllegalStateException(
        "Unsupported member type: " + member.getClass().getSimpleName());
  }
}
