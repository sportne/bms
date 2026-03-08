package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.util.Diagnostic;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared mutable state used while resolving one parsed schema.
 *
 * <p>Keeping this state in one object avoids large parameter lists and keeps resolver helper
 * methods focused on one task each.
 */
final class ResolutionContext {
  final String sourcePath;
  final List<Diagnostic> diagnostics;
  final Map<String, ParsedMessageType> messageTypeByName;
  final Map<String, ParsedFloat> reusableFloatByName;
  final Map<String, ParsedScaledInt> reusableScaledIntByName;
  final Map<String, ParsedArray> reusableArrayByName;
  final Map<String, ParsedVector> reusableVectorByName;
  final Map<String, ParsedBlobArray> reusableBlobArrayByName;
  final Map<String, ParsedBlobVector> reusableBlobVectorByName;
  final Map<String, ParsedVarString> reusableVarStringByName;
  final Set<String> globalTypeNames;
  final Map<String, String> firstSourcePathByGlobalTypeName;
  private final Map<Object, String> sourcePathByNode;

  /**
   * Creates a fresh context for one schema resolution pass.
   *
   * @param sourcePath human-readable source path used in diagnostics
   */
  ResolutionContext(String sourcePath) {
    this(sourcePath, Map.of());
  }

  /**
   * Creates a fresh context for one schema resolution pass with per-node source provenance.
   *
   * @param sourcePath fallback source path used when node-level provenance is unavailable
   * @param sourcePathByNode source path lookup keyed by parsed definition/member object
   */
  ResolutionContext(String sourcePath, Map<Object, String> sourcePathByNode) {
    this.sourcePath = sourcePath;
    this.sourcePathByNode = Objects.requireNonNull(sourcePathByNode, "sourcePathByNode");
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
    firstSourcePathByGlobalTypeName = new LinkedHashMap<>();
  }

  /**
   * Returns the best source path for one parsed node.
   *
   * <p>Falls back to the context-level source path when no node mapping exists.
   *
   * @param node parsed object used by diagnostics
   * @return source path string for diagnostics
   */
  String sourcePathFor(Object node) {
    if (node == null) {
      return sourcePath;
    }
    return sourcePathByNode.getOrDefault(node, sourcePath);
  }
}
