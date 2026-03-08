package io.github.sportne.bms;

import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedImport;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedSpecDocument;
import io.github.sportne.bms.parse.SpecParser;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.validate.SpecValidator;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** Loads one multi-file BMS project, then flattens it into one merged parsed schema. */
final class SpecProjectLoader {
  private final SpecValidator specValidator;
  private final SpecParser specParser;

  /**
   * Creates one loader bound to validator and parser collaborators.
   *
   * @param specValidator XSD validator
   * @param specParser XML parser
   */
  SpecProjectLoader(SpecValidator specValidator, SpecParser specParser) {
    this.specValidator = Objects.requireNonNull(specValidator, "specValidator");
    this.specParser = Objects.requireNonNull(specParser, "specParser");
  }

  /**
   * Loads all root specs (and their recursive imports) into one merged schema.
   *
   * @param rootSpecPaths ordered root input paths from the caller
   * @return merged parsed schema and source-path provenance map
   * @throws BmsException if validation/parsing/import resolution fails
   */
  LoadedProject load(List<Path> rootSpecPaths) throws BmsException {
    Objects.requireNonNull(rootSpecPaths, "rootSpecPaths");
    if (rootSpecPaths.isEmpty()) {
      Diagnostic diagnostic =
          new Diagnostic(
              DiagnosticSeverity.ERROR,
              "COMPILER_MISSING_SPEC_PATH",
              "At least one spec path is required.",
              "",
              -1,
              -1);
      throw new BmsException("No spec paths were provided.", List.of(diagnostic));
    }

    LinkedHashMap<Path, LoadedFile> loadedFilesByPath = new LinkedHashMap<>();
    List<Path> activeImportStack = new ArrayList<>();
    for (Path rootSpecPath : rootSpecPaths) {
      Path normalizedRootPath = normalizePath(rootSpecPath);
      loadRecursively(normalizedRootPath, loadedFilesByPath, activeImportStack);
    }
    return mergeLoadedFiles(List.copyOf(loadedFilesByPath.values()));
  }

  /**
   * Loads one file, then recursively loads all imported files in declaration order.
   *
   * @param specPath normalized spec path
   * @param loadedFilesByPath ordered map of already loaded files
   * @param activeImportStack current DFS stack used for cycle detection
   * @throws BmsException if validation, parsing, or import resolution fails
   */
  private void loadRecursively(
      Path specPath,
      LinkedHashMap<Path, LoadedFile> loadedFilesByPath,
      List<Path> activeImportStack)
      throws BmsException {
    int cycleStartIndex = activeImportStack.indexOf(specPath);
    if (cycleStartIndex >= 0) {
      throw importCycle(specPath, activeImportStack, cycleStartIndex);
    }
    if (loadedFilesByPath.containsKey(specPath)) {
      return;
    }

    activeImportStack.add(specPath);
    try {
      specValidator.validateOrThrow(specPath);
      ParsedSpecDocument parsedSpecDocument = specParser.parseDocument(specPath);
      loadedFilesByPath.put(specPath, new LoadedFile(specPath, parsedSpecDocument.schema()));
      for (ParsedImport parsedImport : parsedSpecDocument.imports()) {
        Path importedPath = resolveImportPath(specPath, parsedImport.path());
        loadRecursively(importedPath, loadedFilesByPath, activeImportStack);
      }
    } finally {
      activeImportStack.remove(activeImportStack.size() - 1);
    }
  }

  /**
   * Returns one normalized absolute path.
   *
   * @param path source path
   * @return normalized absolute path
   */
  private static Path normalizePath(Path path) {
    return path.toAbsolutePath().normalize();
  }

  /**
   * Resolves one import path relative to the including spec file.
   *
   * @param includingSpecPath source file containing the import
   * @param importPath raw import path literal
   * @return normalized import target path
   * @throws BmsException if the import path literal is invalid
   */
  private static Path resolveImportPath(Path includingSpecPath, String importPath)
      throws BmsException {
    try {
      Path parsedImportPath = Path.of(importPath);
      Path baseDirectory =
          includingSpecPath.getParent() == null ? Path.of("") : includingSpecPath.getParent();
      Path resolvedPath =
          parsedImportPath.isAbsolute()
              ? parsedImportPath
              : baseDirectory.resolve(parsedImportPath);
      return normalizePath(resolvedPath);
    } catch (InvalidPathException exception) {
      Diagnostic diagnostic =
          new Diagnostic(
              DiagnosticSeverity.ERROR,
              "IMPORT_INVALID_PATH",
              "Invalid import path '" + importPath + "' in " + includingSpecPath + ".",
              includingSpecPath.toString(),
              -1,
              -1);
      throw new BmsException("Import resolution failed.", List.of(diagnostic));
    }
  }

  /**
   * Builds one cycle diagnostic exception for recursive imports.
   *
   * @param repeatedPath path that closes the cycle
   * @param activeImportStack current DFS stack
   * @param cycleStartIndex index where cycle starts in the stack
   * @return exception that describes the cycle chain
   */
  private static BmsException importCycle(
      Path repeatedPath, List<Path> activeImportStack, int cycleStartIndex) {
    StringJoiner cycleJoiner = new StringJoiner(" -> ");
    for (int index = cycleStartIndex; index < activeImportStack.size(); index++) {
      cycleJoiner.add(activeImportStack.get(index).toString());
    }
    cycleJoiner.add(repeatedPath.toString());

    Path cycleSourcePath = activeImportStack.get(activeImportStack.size() - 1);
    Diagnostic diagnostic =
        new Diagnostic(
            DiagnosticSeverity.ERROR,
            "IMPORT_CYCLE_DETECTED",
            "Import cycle detected: " + cycleJoiner,
            cycleSourcePath.toString(),
            -1,
            -1);
    return new BmsException("Import resolution failed.", List.of(diagnostic));
  }

  /**
   * Merges loaded files into one parsed schema while preserving deterministic order.
   *
   * @param loadedFiles loaded files in traversal order
   * @return merged parsed schema and provenance map
   */
  private static LoadedProject mergeLoadedFiles(List<LoadedFile> loadedFiles) {
    LoadedFile firstFile = loadedFiles.get(0);

    List<ParsedMessageType> messageTypes = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedBitField> bitFields = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedFloat> floats = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedScaledInt> scaledInts = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedArray> arrays = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedVector> vectors = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedBlobArray> blobArrays = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedBlobVector> blobVectors = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedVarString> varStrings = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedChecksum> checksums = new ArrayList<>();
    List<io.github.sportne.bms.model.parsed.ParsedPad> pads = new ArrayList<>();

    IdentityHashMap<Object, String> sourcePathByNode = new IdentityHashMap<>();
    for (LoadedFile loadedFile : loadedFiles) {
      ParsedSchema parsedSchema = loadedFile.parsedSchema();
      String sourcePath = loadedFile.path().toString();

      for (ParsedMessageType parsedMessageType : parsedSchema.messageTypes()) {
        ParsedMessageType adjustedMessageType =
            withDefaultNamespaceOverride(parsedMessageType, parsedSchema.namespace());
        messageTypes.add(adjustedMessageType);
        registerMessageTreeSourcePaths(adjustedMessageType, sourcePath, sourcePathByNode);
      }

      addWithSourcePath(parsedSchema.reusableBitFields(), bitFields, sourcePath, sourcePathByNode);
      addWithSourcePath(parsedSchema.reusableFloats(), floats, sourcePath, sourcePathByNode);
      addWithSourcePath(
          parsedSchema.reusableScaledInts(), scaledInts, sourcePath, sourcePathByNode);
      addWithSourcePath(parsedSchema.reusableArrays(), arrays, sourcePath, sourcePathByNode);
      addWithSourcePath(parsedSchema.reusableVectors(), vectors, sourcePath, sourcePathByNode);
      addWithSourcePath(
          parsedSchema.reusableBlobArrays(), blobArrays, sourcePath, sourcePathByNode);
      addWithSourcePath(
          parsedSchema.reusableBlobVectors(), blobVectors, sourcePath, sourcePathByNode);
      addWithSourcePath(
          parsedSchema.reusableVarStrings(), varStrings, sourcePath, sourcePathByNode);
      addWithSourcePath(parsedSchema.reusableChecksums(), checksums, sourcePath, sourcePathByNode);
      addWithSourcePath(parsedSchema.reusablePads(), pads, sourcePath, sourcePathByNode);
    }

    ParsedSchema mergedSchema =
        new ParsedSchema(
            firstFile.parsedSchema().namespace(),
            messageTypes,
            bitFields,
            floats,
            scaledInts,
            arrays,
            vectors,
            blobArrays,
            blobVectors,
            varStrings,
            checksums,
            pads);
    return new LoadedProject(
        mergedSchema, Collections.unmodifiableMap(new IdentityHashMap<>(sourcePathByNode)));
  }

  /**
   * Applies source-file schema namespace as message fallback when no explicit override exists.
   *
   * @param parsedMessageType source message definition
   * @param sourceSchemaNamespace schema namespace from the source file
   * @return message with explicit namespace override when needed
   */
  private static ParsedMessageType withDefaultNamespaceOverride(
      ParsedMessageType parsedMessageType, String sourceSchemaNamespace) {
    if (parsedMessageType.namespaceOverride() != null) {
      return parsedMessageType;
    }
    return new ParsedMessageType(
        parsedMessageType.name(),
        parsedMessageType.comment(),
        sourceSchemaNamespace,
        parsedMessageType.members());
  }

  /**
   * Appends definitions and records a source-path mapping for each definition.
   *
   * @param sourceValues source definitions
   * @param targetValues merged destination list
   * @param sourcePath source file path for all values
   * @param sourcePathByNode provenance map
   * @param <T> parsed definition type
   */
  private static <T> void addWithSourcePath(
      List<T> sourceValues,
      List<T> targetValues,
      String sourcePath,
      IdentityHashMap<Object, String> sourcePathByNode) {
    for (T sourceValue : sourceValues) {
      targetValues.add(sourceValue);
      sourcePathByNode.put(sourceValue, sourcePath);
    }
  }

  /**
   * Records source-path mappings for one message definition and all nested members.
   *
   * @param parsedMessageType message to traverse
   * @param sourcePath source file path
   * @param sourcePathByNode provenance map
   */
  private static void registerMessageTreeSourcePaths(
      ParsedMessageType parsedMessageType,
      String sourcePath,
      IdentityHashMap<Object, String> sourcePathByNode) {
    sourcePathByNode.put(parsedMessageType, sourcePath);
    for (ParsedMessageMember parsedMessageMember : parsedMessageType.members()) {
      registerMessageMemberSourcePath(parsedMessageMember, sourcePath, sourcePathByNode);
    }
  }

  /**
   * Records source-path mappings for one message member subtree.
   *
   * @param parsedMessageMember member to traverse
   * @param sourcePath source file path
   * @param sourcePathByNode provenance map
   */
  private static void registerMessageMemberSourcePath(
      ParsedMessageMember parsedMessageMember,
      String sourcePath,
      IdentityHashMap<Object, String> sourcePathByNode) {
    sourcePathByNode.put(parsedMessageMember, sourcePath);
    if (parsedMessageMember instanceof ParsedIfBlock parsedIfBlock) {
      for (ParsedMessageMember nestedMember : parsedIfBlock.members()) {
        registerMessageMemberSourcePath(nestedMember, sourcePath, sourcePathByNode);
      }
      return;
    }
    if (parsedMessageMember instanceof ParsedMessageType nestedType) {
      registerMessageTreeSourcePaths(nestedType, sourcePath, sourcePathByNode);
    }
  }

  /**
   * Loaded multi-file parsed project.
   *
   * @param parsedSchema merged parsed schema
   * @param sourcePathByNode provenance map for semantic diagnostics
   */
  record LoadedProject(ParsedSchema parsedSchema, Map<Object, String> sourcePathByNode) {}

  private record LoadedFile(Path path, ParsedSchema parsedSchema) {}
}
