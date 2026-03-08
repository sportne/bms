package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBlobArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedChecksum;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedLengthMode;
import io.github.sportne.bms.model.resolved.ResolvedPad;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedVarString;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves all schema-level reusable definitions after type registration is complete. */
final class ReusableTypeResolver {
  /** Prevents instantiation of this static helper class. */
  private ReusableTypeResolver() {}

  /**
   * Resolves all schema-level reusable definitions.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param context shared semantic-resolution state
   * @return grouped reusable-definition resolution result
   */
  static ReusableResolution resolveReusableDefinitions(
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
      SemanticValidationRules.validateFloatScaleRules(
          parsedFloat, context.sourcePath, context.diagnostics);
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
            SemanticValidationRules.error(
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
          MessageMemberResolver.resolveTypeRef(
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
          MessageMemberResolver.resolveTypeRef(
              parsedVector.elementTypeName(), "reusable vector " + parsedVector.name(), context);
      ResolvedLengthMode lengthMode =
          LengthModeResolver.resolveLengthMode(
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
          LengthModeResolver.resolveLengthMode(
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
          LengthModeResolver.resolveLengthMode(
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
   * Resolves reusable bitfield definitions and enforces unique schema-level names.
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
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_ROOT_BIT_FIELD_NAME",
                "Duplicate schema-level bitField name: " + parsedBitField.name(),
                context.sourcePath));
      }
      resolvedReusableBitFields.add(
          BitFieldResolver.resolveBitField(
              parsedBitField, context.sourcePath, context.diagnostics));
    }
    return resolvedReusableBitFields;
  }

  /**
   * Groups resolved reusable definitions so callers can return them together.
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
  record ReusableResolution(
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
}
