package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses length-mode declarations for collection and varString elements. */
final class LengthModeParser {

  /** Creates one length-mode parser. */
  LengthModeParser() {}

  /**
   * Parses exactly one vector/blobVector/varString length mode child.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on the parent element
   * @param parentName parent element name used in diagnostics
   * @param allowTerminatorField whether {@code terminatorField} child is allowed
   * @return parsed length mode
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if missing or unsupported child elements are found
   */
  ParsedLengthMode parseLengthMode(
      Path specPath, XMLStreamReader reader, String parentName, boolean allowTerminatorField)
      throws XMLStreamException, BmsException {
    ParsedLengthMode lengthMode = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (lengthMode != null) {
          throw ParserSupport.parserError(
              specPath,
              reader,
              "PARSER_INVALID_FIELD_CONTENT",
              "<" + parentName + "> supports exactly one length mode child.");
        }
        lengthMode =
            switch (reader.getLocalName()) {
              case "countField" -> parseCountFieldLength(specPath, reader);
              case "terminatorValue" -> parseTerminatorValueLength(specPath, reader);
              case "terminatorField" -> {
                if (!allowTerminatorField) {
                  throw ParserSupport.parserError(
                      specPath,
                      reader,
                      "PARSER_UNSUPPORTED_ELEMENT",
                      "<" + parentName + "> does not support <terminatorField>.");
                }
                yield parseTerminatorField(specPath, reader);
              }
              default -> throw ParserSupport.parserError(
                  specPath,
                  reader,
                  "PARSER_UNSUPPORTED_ELEMENT",
                  "Unsupported element inside <"
                      + parentName
                      + ">: <"
                      + reader.getLocalName()
                      + ">.");
            };
      }
      if (event == XMLStreamConstants.END_ELEMENT && parentName.equals(reader.getLocalName())) {
        if (lengthMode == null) {
          throw ParserSupport.parserError(
              specPath,
              reader,
              "PARSER_MISSING_CHILD_ELEMENT",
              "<" + parentName + "> requires one length mode child.");
        }
        return lengthMode;
      }
    }

    throw ParserSupport.singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <" + parentName + ">.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses one {@code <countField>} length mode node.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <countField>}
   * @return parsed count-field length mode
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedCountFieldLength parseCountFieldLength(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String ref = ParserSupport.requireAttribute(specPath, reader, "ref");
    ParserSupport.expectNoNestedElements(specPath, reader, "countField");
    return new ParsedCountFieldLength(ref);
  }

  /**
   * Parses one {@code <terminatorValue>} length mode node.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <terminatorValue>}
   * @return parsed terminator-value length mode
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedTerminatorValueLength parseTerminatorValueLength(
      Path specPath, XMLStreamReader reader) throws XMLStreamException, BmsException {
    String value = ParserSupport.requireAttribute(specPath, reader, "value");
    ParserSupport.expectNoNestedElements(specPath, reader, "terminatorValue");
    return new ParsedTerminatorValueLength(value);
  }

  /**
   * Parses one recursive {@code <terminatorField>} node.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <terminatorField>}
   * @return parsed terminator-field node
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if nested structure is invalid
   */
  private ParsedTerminatorField parseTerminatorField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    ParsedTerminatorNode next = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (next != null) {
          throw ParserSupport.parserError(
              specPath,
              reader,
              "PARSER_INVALID_FIELD_CONTENT",
              "<terminatorField> supports at most one nested node.");
        }
        next =
            switch (reader.getLocalName()) {
              case "terminatorField" -> parseTerminatorField(specPath, reader);
              case "terminatorMatch" -> parseTerminatorMatch(specPath, reader);
              default -> throw ParserSupport.parserError(
                  specPath,
                  reader,
                  "PARSER_UNSUPPORTED_ELEMENT",
                  "Unsupported element inside <terminatorField>: <" + reader.getLocalName() + ">.");
            };
      }
      if (event == XMLStreamConstants.END_ELEMENT
          && "terminatorField".equals(reader.getLocalName())) {
        return new ParsedTerminatorField(name, next);
      }
    }

    throw ParserSupport.singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <terminatorField>.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses one {@code <terminatorMatch>} leaf node.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <terminatorMatch>}
   * @return parsed terminator-match node
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedTerminatorMatch parseTerminatorMatch(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String value = ParserSupport.requireAttribute(specPath, reader, "value");
    ParserSupport.expectNoNestedElements(specPath, reader, "terminatorMatch");
    return new ParsedTerminatorMatch(value);
  }
}
