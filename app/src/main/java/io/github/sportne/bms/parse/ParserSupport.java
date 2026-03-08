package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Shared XML attribute and diagnostic helpers used by parser collaborators. */
final class ParserSupport {
  private static final BigInteger MAX_UNSIGNED_LONG =
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

  /** Prevents instantiation of this static helper class. */
  private ParserSupport() {}

  /**
   * Ensures the current element has no nested start elements.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param elementName element expected to close next
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if a nested element is found
   */
  static void expectNoNestedElements(Path specPath, XMLStreamReader reader, String elementName)
      throws XMLStreamException, BmsException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_FIELD_CONTENT",
            "<" + elementName + "> does not support nested elements.");
      }
      if (event == XMLStreamConstants.END_ELEMENT && elementName.equals(reader.getLocalName())) {
        return;
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <" + elementName + ">.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses an optional {@code endian} attribute.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param context element name used in diagnostic text
   * @return parsed endian value, or {@code null} when not present
   * @throws BmsException if the attribute value is invalid
   */
  static Endian parseOptionalEndian(Path specPath, XMLStreamReader reader, String context)
      throws BmsException {
    String endianValue = reader.getAttributeValue(null, "endian");
    if (endianValue == null) {
      return null;
    }
    try {
      return Endian.fromXml(endianValue);
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ENDIAN",
          "Unsupported endian value on <" + context + ">: " + endianValue);
    }
  }

  /**
   * Parses an integer attribute that must be positive.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param attributeName attribute name used in diagnostics
   * @param value raw attribute value text
   * @return parsed integer value
   * @throws BmsException if the value is not a positive integer
   */
  static int parsePositiveInteger(
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

  /**
   * Parses an integer attribute limited to the range {@code 0..255}.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param attributeName attribute name used in diagnostics
   * @param value raw attribute value text
   * @return parsed integer value
   * @throws BmsException if the value is not in {@code 0..255}
   */
  static int parseUnsignedByte(
      Path specPath, XMLStreamReader reader, String attributeName, String value)
      throws BmsException {
    try {
      int parsed = Integer.parseInt(value);
      if (parsed < 0 || parsed > 255) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_ATTRIBUTE",
            "Attribute " + attributeName + " must be between 0 and 255.");
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

  /**
   * Parses an integer attribute limited to the unsigned 64-bit range.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param attributeName attribute name used in diagnostics
   * @param value raw attribute value text
   * @return parsed value as {@link BigInteger}
   * @throws BmsException if the value is outside {@code 0..2^64-1}
   */
  static BigInteger parseUnsignedLong(
      Path specPath, XMLStreamReader reader, String attributeName, String value)
      throws BmsException {
    try {
      BigInteger parsed = new BigInteger(value);
      if (parsed.signum() < 0 || parsed.compareTo(MAX_UNSIGNED_LONG) > 0) {
        throw parserError(
            specPath,
            reader,
            "PARSER_INVALID_ATTRIBUTE",
            "Attribute " + attributeName + " must be between 0 and 2^64-1.");
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

  /**
   * Parses a decimal attribute.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param attributeName attribute name used in diagnostics
   * @param value raw attribute value text
   * @return parsed decimal value
   * @throws BmsException if the value is not a decimal number
   */
  static BigDecimal parseDecimal(
      Path specPath, XMLStreamReader reader, String attributeName, String value)
      throws BmsException {
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Attribute " + attributeName + " must be a decimal number.");
    }
  }

  /**
   * Normalizes a QName-like string by removing any namespace prefix.
   *
   * @param qNameLiteral raw QName-like literal from XML
   * @return local-name portion without prefix
   */
  static String normalizeQNameLiteral(String qNameLiteral) {
    String trimmed = qNameLiteral.trim();
    int colonIndex = trimmed.indexOf(':');
    if (colonIndex < 0) {
      return trimmed;
    }
    return trimmed.substring(colonIndex + 1);
  }

  /**
   * Reads one required attribute and rejects missing/blank values.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param name required attribute name
   * @return non-blank attribute value
   * @throws BmsException if the attribute is missing or blank
   */
  static String requireAttribute(Path specPath, XMLStreamReader reader, String name)
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

  /**
   * Creates an exception for an unsupported child element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param parentContext parent element name
   * @return exception with one diagnostic
   */
  static BmsException unsupportedElement(
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

  /**
   * Creates an exception at the reader's current location.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @return exception with one diagnostic
   */
  static BmsException parserError(
      Path specPath, XMLStreamReader reader, String code, String message) {
    return singleDiagnosticException(
        code,
        message,
        specPath,
        reader.getLocation().getLineNumber(),
        reader.getLocation().getColumnNumber());
  }

  /**
   * Creates an exception that contains exactly one diagnostic.
   *
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @param specPath source file path used in diagnostics
   * @param line 1-based line number, or negative when unknown
   * @param column 1-based column number, or negative when unknown
   * @return exception with one diagnostic
   */
  static BmsException singleDiagnosticException(
      String code, String message, Path specPath, int line, int column) {
    Diagnostic diagnostic =
        new Diagnostic(DiagnosticSeverity.ERROR, code, message, specPath.toString(), line, column);
    return new BmsException(message, List.of(diagnostic));
  }
}
