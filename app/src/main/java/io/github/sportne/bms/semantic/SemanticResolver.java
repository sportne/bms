package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.util.BmsException;
import java.util.List;

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
    ResolutionContext context = new ResolutionContext(sourcePath);
    SemanticValidationRules.validateNamespace(
        parsedSchema.namespace(), "schema@namespace", context.sourcePath, context.diagnostics);
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
