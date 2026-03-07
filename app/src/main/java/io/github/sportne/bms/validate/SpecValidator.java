package io.github.sportne.bms.validate;

import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import io.github.sportne.bms.util.Diagnostics;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Validates BMS XML files against the XSD.
 *
 * <p>Think of this as the first safety gate. It checks the XML structure and required attributes
 * before parsing and semantic checks.
 */
public final class SpecValidator {
  private final Schema schema;

  private SpecValidator(Schema schema) {
    this.schema = schema;
  }

  /** Creates a validator from an XSD file path. */
  public static SpecValidator fromXsd(Path xsdPath) throws BmsException {
    try {
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      try (InputStream inputStream = Files.newInputStream(xsdPath)) {
        Schema loadedSchema = schemaFactory.newSchema(new StreamSource(inputStream));
        return new SpecValidator(loadedSchema);
      }
    } catch (IOException | SAXException exception) {
      Diagnostic diagnostic =
          new Diagnostic(
              DiagnosticSeverity.ERROR,
              "VALIDATOR_SCHEMA_LOAD_FAILED",
              "Failed to load XSD: " + exception.getMessage(),
              xsdPath.toString(),
              -1,
              -1);
      throw new BmsException("Unable to initialize XSD validator.", List.of(diagnostic));
    }
  }

  public List<Diagnostic> validate(Path specPath) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    var validator = schema.newValidator();
    validator.setErrorHandler(new CollectingErrorHandler(specPath, diagnostics));

    try (InputStream inputStream = Files.newInputStream(specPath)) {
      validator.validate(new StreamSource(inputStream));
    } catch (SAXException exception) {
      if (!Diagnostics.hasErrors(diagnostics)) {
        diagnostics.add(
            new Diagnostic(
                DiagnosticSeverity.ERROR,
                "XSD_VALIDATION_FAILED",
                "XSD validation failed: " + exception.getMessage(),
                specPath.toString(),
                -1,
                -1));
      }
    } catch (IOException exception) {
      diagnostics.add(
          new Diagnostic(
              DiagnosticSeverity.ERROR,
              "XSD_IO_ERROR",
              "Failed to read XML spec: " + exception.getMessage(),
              specPath.toString(),
              -1,
              -1));
    }

    return List.copyOf(diagnostics);
  }

  /** Validates a spec and throws when any error-level diagnostic exists. */
  public void validateOrThrow(Path specPath) throws BmsException {
    List<Diagnostic> diagnostics = validate(specPath);
    if (Diagnostics.hasErrors(diagnostics)) {
      throw new BmsException("XSD validation failed.", diagnostics);
    }
  }

  private static final class CollectingErrorHandler implements ErrorHandler {
    private final Path specPath;
    private final List<Diagnostic> diagnostics;

    private CollectingErrorHandler(Path specPath, List<Diagnostic> diagnostics) {
      this.specPath = specPath;
      this.diagnostics = diagnostics;
    }

    @Override
    public void warning(SAXParseException exception) {
      diagnostics.add(toDiagnostic("XSD_WARNING", DiagnosticSeverity.WARNING, exception));
    }

    @Override
    public void error(SAXParseException exception) {
      diagnostics.add(toDiagnostic("XSD_ERROR", DiagnosticSeverity.ERROR, exception));
    }

    @Override
    public void fatalError(SAXParseException exception) {
      diagnostics.add(toDiagnostic("XSD_FATAL", DiagnosticSeverity.ERROR, exception));
    }

    private Diagnostic toDiagnostic(
        String code, DiagnosticSeverity severity, SAXParseException exception) {
      return new Diagnostic(
          severity,
          code,
          exception.getMessage(),
          specPath.toString(),
          exception.getLineNumber(),
          exception.getColumnNumber());
    }
  }
}
