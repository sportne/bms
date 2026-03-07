package io.github.sportne.bms;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.parse.SpecParser;
import io.github.sportne.bms.semantic.SemanticResolver;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.validate.SpecValidator;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the BMS front-end pipeline.
 *
 * <p>In simple terms, this class does the "thinking" part before code generation:
 *
 * <ol>
 *   <li>Check XML structure using the XSD.
 *   <li>Parse XML into Java objects.
 *   <li>Run semantic checks and build a resolved model.
 * </ol>
 */
public final class BmsCompiler {
  private final SpecValidator specValidator;
  private final SpecParser specParser;
  private final SemanticResolver semanticResolver;

  /**
   * Creates a compiler using the provided XSD path.
   *
   * @param xsdPath path to the BMS XML schema file used for validation
   * @throws BmsException if the XSD cannot be loaded
   */
  public BmsCompiler(Path xsdPath) throws BmsException {
    this(SpecValidator.fromXsd(xsdPath), new SpecParser(), new SemanticResolver());
  }

  /**
   * Internal constructor used by tests to inject collaborators directly.
   *
   * @param specValidator validator instance for XSD checks
   * @param specParser parser instance for XML-to-parsed-model conversion
   * @param semanticResolver semantic resolver for parsed-to-resolved conversion
   */
  BmsCompiler(
      SpecValidator specValidator, SpecParser specParser, SemanticResolver semanticResolver) {
    this.specValidator = specValidator;
    this.specParser = specParser;
    this.semanticResolver = semanticResolver;
  }

  /**
   * Runs the front-end pipeline for one spec file.
   *
   * @param specPath path to the XML spec file
   * @return resolved schema ready for code generation
   * @throws BmsException if validation, parsing, or semantic checks fail
   */
  public ResolvedSchema compile(Path specPath) throws BmsException {
    specValidator.validateOrThrow(specPath);
    ParsedSchema parsedSchema = specParser.parse(specPath);
    return semanticResolver.resolve(parsedSchema, specPath.toString());
  }

  /** Finds the default XSD path by walking up from the current working directory. */
  public static Path defaultXsdPath() {
    Path current = Path.of("").toAbsolutePath();
    while (current != null) {
      Path candidate = current.resolve("spec").resolve("xsd").resolve("BinaryMessageSchema.xsd");
      if (Files.exists(candidate)) {
        return candidate;
      }
      current = current.getParent();
    }
    return Path.of("spec", "xsd", "BinaryMessageSchema.xsd");
  }
}
