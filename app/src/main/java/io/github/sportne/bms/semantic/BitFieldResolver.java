package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedBitFlag;
import io.github.sportne.bms.model.resolved.ResolvedBitSegment;
import io.github.sportne.bms.model.resolved.ResolvedBitVariant;
import io.github.sportne.bms.util.Diagnostic;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves parsed bitfield definitions into resolved bitfield objects. */
final class BitFieldResolver {
  /** Prevents instantiation of this static helper class. */
  private BitFieldResolver() {}

  /**
   * Resolves one parsed bitfield, including nested validation and conversion.
   *
   * @param parsedBitField parsed bitfield object
   * @param sourcePath source path used in diagnostics
   * @param diagnostics list that receives semantic errors
   * @return resolved bitfield object
   */
  static ResolvedBitField resolveBitField(
      ParsedBitField parsedBitField, String sourcePath, List<Diagnostic> diagnostics) {
    SemanticValidationRules.validateIdentifier(
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
      SemanticValidationRules.validateIdentifier(
          parsedFlag.name(),
          "SEMANTIC_INVALID_FLAG_NAME",
          "Flag name must be a valid identifier: ",
          sourcePath,
          diagnostics);

      if (!flagNames.add(parsedFlag.name())) {
        diagnostics.add(
            SemanticValidationRules.error(
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
      SemanticValidationRules.validateIdentifier(
          parsedSegment.name(),
          "SEMANTIC_INVALID_SEGMENT_NAME",
          "Segment name must be a valid identifier: ",
          sourcePath,
          diagnostics);

      if (!segmentNames.add(parsedSegment.name())) {
        diagnostics.add(
            SemanticValidationRules.error(
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
      SemanticValidationRules.validateIdentifier(
          parsedVariant.name(),
          "SEMANTIC_INVALID_VARIANT_NAME",
          "Variant name must be a valid identifier: ",
          sourcePath,
          diagnostics);
      if (!variantNames.add(parsedVariant.name())) {
        diagnostics.add(
            SemanticValidationRules.error(
                "SEMANTIC_DUPLICATE_VARIANT_NAME",
                "Duplicate variant name in segment "
                    + parsedSegment.name()
                    + ": "
                    + parsedVariant.name(),
                sourcePath));
      }
      if (parsedVariant.value().compareTo(maxAllowedValue) > 0) {
        diagnostics.add(
            SemanticValidationRules.error(
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
   * Adds a diagnostic when a bitfield member name is reused.
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
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_BIT_MEMBER_NAME",
              "Duplicate bitField member name in " + bitFieldName + ": " + memberName,
              sourcePath));
    }
  }

  /**
   * Validates one flag position against bit-width and duplicate-position rules.
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
          SemanticValidationRules.error(
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
          SemanticValidationRules.error(
              "SEMANTIC_DUPLICATE_FLAG_POSITION",
              "Duplicate flag position: " + parsedFlag.position(),
              sourcePath));
    }
  }

  /**
   * Validates one segment range against ordering and bit-width rules.
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
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_SEGMENT_RANGE",
              "Segment range must satisfy from <= to for segment " + parsedSegment.name(),
              sourcePath));
    }
    if (parsedSegment.fromBit() >= bitWidth || parsedSegment.toBit() >= bitWidth) {
      diagnostics.add(
          SemanticValidationRules.error(
              "SEMANTIC_INVALID_SEGMENT_RANGE",
              "Segment "
                  + parsedSegment.name()
                  + " is outside bitField size "
                  + parsedBitField.size().xmlValue(),
              sourcePath));
    }
  }
}
