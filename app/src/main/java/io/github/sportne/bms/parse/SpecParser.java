package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * StAX parser orchestrator for BMS XML specs.
 *
 * <p>This class owns stream setup and high-level control flow. Element-specific parsing lives in
 * focused collaborators.
 */
public final class SpecParser {
  private static final String ROOT_ELEMENT = "schema";

  private final XMLInputFactory inputFactory;
  private final RootElementParser rootElementParser;

  /** Creates a parser and disables XML features that are not needed for BMS files. */
  public SpecParser() {
    inputFactory = XMLInputFactory.newFactory();
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    MessageElementParser messageElementParser = new MessageElementParser();
    rootElementParser = new RootElementParser(messageElementParser);
  }

  /**
   * Parses one XML spec file into the parsed model layer.
   *
   * @param specPath path to the XML spec file
   * @return parsed schema object
   * @throws BmsException if reading or parsing fails
   */
  public ParsedSchema parse(Path specPath) throws BmsException {
    try (InputStream inputStream = Files.newInputStream(specPath)) {
      XMLStreamReader reader = inputFactory.createXMLStreamReader(inputStream);
      try {
        moveToRootElement(reader, specPath);
        if (!ROOT_ELEMENT.equals(reader.getLocalName())) {
          throw ParserSupport.parserError(
              specPath, reader, "PARSER_INVALID_ROOT", "Root element must be <schema>.");
        }

        String schemaNamespace = ParserSupport.requireAttribute(specPath, reader, "namespace");
        RootElementParser.RootItems rootItems =
            rootElementParser.parseRootChildren(specPath, reader);
        return new ParsedSchema(
            schemaNamespace,
            rootItems.messageTypes(),
            rootItems.bitFields(),
            rootItems.floats(),
            rootItems.scaledInts(),
            rootItems.arrays(),
            rootItems.vectors(),
            rootItems.blobArrays(),
            rootItems.blobVectors(),
            rootItems.varStrings(),
            rootItems.checksums(),
            rootItems.pads());
      } finally {
        reader.close();
      }
    } catch (IOException exception) {
      throw ParserSupport.singleDiagnosticException(
          "PARSER_IO_ERROR",
          "Failed to read XML spec: " + exception.getMessage(),
          specPath,
          -1,
          -1);
    } catch (XMLStreamException exception) {
      int line = exception.getLocation() == null ? -1 : exception.getLocation().getLineNumber();
      int column = exception.getLocation() == null ? -1 : exception.getLocation().getColumnNumber();
      throw ParserSupport.singleDiagnosticException(
          "PARSER_XML_STREAM_ERROR",
          "Malformed XML: " + exception.getMessage(),
          specPath,
          line,
          column);
    }
  }

  /**
   * Advances the reader to the first XML start element.
   *
   * @param reader active XML reader
   * @param specPath source file path used in diagnostics
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if the XML document has no root element
   */
  private static void moveToRootElement(XMLStreamReader reader, Path specPath)
      throws XMLStreamException, BmsException {
    while (reader.hasNext() && reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
      reader.next();
    }
    if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
      throw ParserSupport.singleDiagnosticException(
          "PARSER_EMPTY_DOCUMENT", "XML document has no root element.", specPath, -1, -1);
    }
  }
}
