package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.util.BmsException;
import java.util.List;
import java.util.Map;

/**
 * Converts parsed XML objects into resolved objects used by generators.
 *
 * <p>This class is a coordinator for semantic resolution stages. Detailed rule logic lives in
 * focused package-private collaborators.
 */
public final class SemanticResolver {
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
    return resolve(parsedSchema, sourcePath, Map.of());
  }

  /**
   * Builds the resolved schema with optional per-node source provenance.
   *
   * @param parsedSchema parsed schema produced by the parser
   * @param sourcePath fallback source path used in diagnostics
   * @param sourcePathByNode optional provenance map keyed by parsed node object
   * @return resolved schema ready for code generation
   * @throws BmsException if one or more semantic errors are found
   */
  public ResolvedSchema resolve(
      ParsedSchema parsedSchema, String sourcePath, Map<Object, String> sourcePathByNode)
      throws BmsException {
    ResolutionContext context = new ResolutionContext(sourcePath, sourcePathByNode);
    SemanticValidationRules.validateNamespace(
        parsedSchema.namespace(),
        "schema@namespace",
        context.sourcePathFor(parsedSchema),
        context.diagnostics);
    TypeRegistryBuilder.registerSchemaLevelTypes(parsedSchema, context);

    ReusableTypeResolver.ReusableResolution reusableResolution =
        ReusableTypeResolver.resolveReusableDefinitions(parsedSchema, context);
    List<ResolvedMessageType> resolvedMessageTypes =
        MessageMemberResolver.resolveMessageTypes(
            parsedSchema.namespace(), parsedSchema.messageTypes(), context);

    SemanticValidationRules.throwIfDiagnosticsContainErrors(context.diagnostics);
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
}
