package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * StAX parser for the foundation XML subset.
 *
 * <p>Supported today:
 *
 * <ul>
 *   <li>{@code <schema>}
 *   <li>{@code <messageType>}
 *   <li>{@code <field>}
 * </ul>
 *
 * <p>If an unsupported XML element appears, parsing fails fast with a clear diagnostic.
 */
public final class SpecParser {
  private static final String ROOT_ELEMENT = "schema";

  private final XMLInputFactory inputFactory;

  public SpecParser() {
    inputFactory = XMLInputFactory.newFactory();
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
  }

  /** Parses one XML spec file into the parsed model layer. */
  public ParsedSchema parse(Path specPath) throws BmsException {
    try (InputStream inputStream = Files.newInputStream(specPath)) {
      XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream);
      try {
        moveToRootElement(reader, specPath);
        if (!ROOT_ELEMENT.equals(reader.getLocalName())) {
          throw parserError(
              specPath, reader, "PARSER_INVALID_ROOT", "Root element must be <schema>.");
        }

        String schemaNamespace = requireAttribute(specPath, reader, "namespace");
        List<ParsedMessageType> messageTypes = parseRootChildren(specPath, reader);
        return new ParsedSchema(schemaNamespace, messageTypes);
      } finally {
        reader.close();
      }
    } catch (IOException exception) {
      throw singleDiagnosticException(
          "PARSER_IO_ERROR",
          "Failed to read XML spec: " + exception.getMessage(),
          specPath,
          -1,
          -1);
    } catch (XMLStreamException exception) {
      int line = exception.getLocation() == null ? -1 : exception.getLocation().getLineNumber();
      int column = exception.getLocation() == null ? -1 : exception.getLocation().getColumnNumber();
      throw singleDiagnosticException(
          "PARSER_XML_STREAM_ERROR",
          "Malformed XML: " + exception.getMessage(),
          specPath,
          line,
          column);
    }
  }

  private static void moveToRootElement(XMLStreamReader reader, Path specPath)
      throws XMLStreamException, BmsException {
    while (reader.hasNext() && reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
      reader.next();
    }
    if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
      throw singleDiagnosticException(
          "PARSER_EMPTY_DOCUMENT", "XML document has no root element.", specPath, -1, -1);
    }
  }

  private List<ParsedMessageType> parseRootChildren(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    List<ParsedMessageType> messageTypes = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if ("messageType".equals(reader.getLocalName())) {
          messageTypes.add(parseMessageType(specPath, reader));
          continue;
        }
        throw unsupportedElement(specPath, reader, "root");
      }
      if (event == XMLStreamConstants.END_ELEMENT && ROOT_ELEMENT.equals(reader.getLocalName())) {
        return List.copyOf(messageTypes);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <schema>.",
        specPath,
        -1,
        -1);
  }

  private ParsedMessageType parseMessageType(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String comment = requireAttribute(specPath, reader, "comment");
    String namespaceOverride = reader.getAttributeValue(null, "namespace");

    List<ParsedField> fields = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if ("field".equals(reader.getLocalName())) {
          fields.add(parseField(specPath, reader));
          continue;
        }
        throw unsupportedElement(specPath, reader, "messageType");
      }
      if (event == XMLStreamConstants.END_ELEMENT && "messageType".equals(reader.getLocalName())) {
        return new ParsedMessageType(name, comment, namespaceOverride, fields);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <messageType>.",
        specPath,
        -1,
        -1);
  }

  private ParsedField parseField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String rawTypeName = requireAttribute(specPath, reader, "type");
    String comment = requireAttribute(specPath, reader, "comment");

    String normalizedTypeName = normalizeQNameLiteral(rawTypeName);

    String lengthValue = reader.getAttributeValue(null, "length");
    Integer length =
        lengthValue == null ? null : parsePositiveInteger(specPath, reader, "length", lengthValue);

    Endian endian = null;
    String endianValue = reader.getAttributeValue(null, "endian");
    if (endianValue != null) {
      try {
        endian = Endian.fromXml(endianValue);
      } catch (IllegalArgumentException exception) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_ENDIAN",
            "Unsupported endian value on <field>: " + endianValue);
      }
    }

    String fixed = reader.getAttributeValue(null, "fixed");

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_FIELD_CONTENT",
            "<field> does not support nested elements.");
      }
      if (event == XMLStreamConstants.END_ELEMENT && "field".equals(reader.getLocalName())) {
        return new ParsedField(name, normalizedTypeName, length, endian, fixed, comment);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF", "Unexpected end of file while reading <field>.", specPath, -1, -1);
  }

  private static int parsePositiveInteger(
      Path specPath, XMLStreamReader reader, String attributeName, String value)
      throws BmsException {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_ATTRIBUTE",
            "Attribute " + attributeName + " must be positive.");
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Attribute " + attributeName + " must be an integer.");
    }
  }

  private static String normalizeQNameLiteral(String qNameLiteral) {
    String trimmed = qNameLiteral.trim();
    int colonIndex = trimmed.indexOf(':');
    if (colonIndex < 0) {
      return trimmed;
    }
    return trimmed.substring(colonIndex + 1);
  }

  private static String requireAttribute(Path specPath, XMLStreamReader reader, String name)
      throws BmsException {
    String value = reader.getAttributeValue(null, name);
    if (value == null || value.isBlank()) {
      throw parserError(
          specPath,
          reader,
          "PARSER_MISSING_ATTRIBUTE",
          "Missing required attribute '" + name + "' on <" + reader.getLocalName() + ">.");
    }
    return value;
  }

  private static BmsException unsupportedElement(
      Path specPath, XMLStreamReader reader, String parentContext) {
    return parserError(
        specPath,
        reader,
        "PARSER_UNSUPPORTED_ELEMENT",
        "Unsupported element <"
            + reader.getLocalName()
            + "> under <"
            + parentContext
            + "> in foundation v1.");
  }

  private static BmsException parserError(
      Path specPath, XMLStreamReader reader, String code, String message) {
    return singleDiagnosticException(
        code,
        message,
        specPath,
        reader.getLocation().getLineNumber(),
        reader.getLocation().getColumnNumber());
  }

  private static BmsException singleDiagnosticException(
      String code, String message, Path specPath, int line, int column) {
    Diagnostic diagnostic =
        new Diagnostic(DiagnosticSeverity.ERROR, code, message, specPath.toString(), line, column);
    return new BmsException(message, List.of(diagnostic));
  }
}
