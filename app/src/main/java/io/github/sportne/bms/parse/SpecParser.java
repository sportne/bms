package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;
import io.github.sportne.bms.util.Diagnostic;
import io.github.sportne.bms.util.DiagnosticSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * StAX parser for the current front-end slice.
 *
 * <p>Supported today:
 *
 * <ul>
 *   <li>{@code <schema>}
 *   <li>{@code <messageType>}
 *   <li>{@code <field>}
 *   <li>{@code <bitField>}
 *   <li>{@code <float>}
 *   <li>{@code <scaledInt>}
 * </ul>
 *
 * <p>If an unsupported XML element appears, parsing fails fast with a clear diagnostic.
 */
public final class SpecParser {
  private static final String ROOT_ELEMENT = "schema";
  private static final BigInteger MAX_UNSIGNED_LONG =
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

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
        RootItems rootItems = parseRootChildren(specPath, reader);
        return new ParsedSchema(
            schemaNamespace,
            rootItems.messageTypes(),
            rootItems.bitFields(),
            rootItems.floats(),
            rootItems.scaledInts());
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

  private RootItems parseRootChildren(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    List<ParsedMessageType> messageTypes = new ArrayList<>();
    List<ParsedBitField> bitFields = new ArrayList<>();
    List<ParsedFloat> floats = new ArrayList<>();
    List<ParsedScaledInt> scaledInts = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "messageType" -> messageTypes.add(parseMessageType(specPath, reader));
          case "bitField" -> bitFields.add(parseBitField(specPath, reader));
          case "float" -> floats.add(parseFloat(specPath, reader));
          case "scaledInt" -> scaledInts.add(parseScaledInt(specPath, reader));
          default -> throw unsupportedElement(specPath, reader, "root");
        }
      }
      if (event == XMLStreamConstants.END_ELEMENT && ROOT_ELEMENT.equals(reader.getLocalName())) {
        return new RootItems(messageTypes, bitFields, floats, scaledInts);
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

    List<ParsedMessageMember> members = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "field" -> members.add(parseField(specPath, reader));
          case "bitField" -> members.add(parseBitField(specPath, reader));
          case "float" -> members.add(parseFloat(specPath, reader));
          case "scaledInt" -> members.add(parseScaledInt(specPath, reader));
          default -> throw unsupportedElement(specPath, reader, "messageType");
        }
      }
      if (event == XMLStreamConstants.END_ELEMENT && "messageType".equals(reader.getLocalName())) {
        return new ParsedMessageType(name, comment, namespaceOverride, members);
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

    Endian endian = parseOptionalEndian(specPath, reader, "field");
    String fixed = reader.getAttributeValue(null, "fixed");

    expectNoNestedElements(specPath, reader, "field");
    return new ParsedField(name, normalizedTypeName, length, endian, fixed, comment);
  }

  private ParsedBitField parseBitField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String rawSize = requireAttribute(specPath, reader, "size");
    String comment = requireAttribute(specPath, reader, "comment");

    BitFieldSize size;
    try {
      size = BitFieldSize.fromXml(rawSize);
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported bitField size value: " + rawSize);
    }

    Endian endian = parseOptionalEndian(specPath, reader, "bitField");
    List<ParsedBitFlag> flags = new ArrayList<>();
    List<ParsedBitSegment> segments = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "flag" -> flags.add(parseBitFlag(specPath, reader));
          case "segment" -> segments.add(parseBitSegment(specPath, reader));
          default -> throw parserError(
              specPath,
              reader,
              "PARSER_UNSUPPORTED_ELEMENT",
              "Unsupported element inside <bitField>: <" + reader.getLocalName() + ">.");
        }
      }
      if (event == XMLStreamConstants.END_ELEMENT && "bitField".equals(reader.getLocalName())) {
        return new ParsedBitField(size, endian, comment, flags, segments);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <bitField>.",
        specPath,
        -1,
        -1);
  }

  private ParsedBitFlag parseBitFlag(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    int position =
        parseUnsignedByte(
            specPath, reader, "position", requireAttribute(specPath, reader, "position"));
    String comment = requireAttribute(specPath, reader, "comment");

    expectNoNestedElements(specPath, reader, "flag");
    return new ParsedBitFlag(name, position, comment);
  }

  private ParsedBitSegment parseBitSegment(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    int from =
        parseUnsignedByte(specPath, reader, "from", requireAttribute(specPath, reader, "from"));
    int to = parseUnsignedByte(specPath, reader, "to", requireAttribute(specPath, reader, "to"));
    String comment = requireAttribute(specPath, reader, "comment");

    List<ParsedBitVariant> variants = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (!"variant".equals(reader.getLocalName())) {
          throw parserError(
              specPath,
              reader,
              "PARSER_UNSUPPORTED_ELEMENT",
              "Unsupported element inside <segment>: <" + reader.getLocalName() + ">.");
        }
        variants.add(parseBitVariant(specPath, reader));
      }
      if (event == XMLStreamConstants.END_ELEMENT && "segment".equals(reader.getLocalName())) {
        return new ParsedBitSegment(name, from, to, comment, variants);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <segment>.",
        specPath,
        -1,
        -1);
  }

  private ParsedBitVariant parseBitVariant(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    BigInteger value =
        parseUnsignedLong(specPath, reader, "value", requireAttribute(specPath, reader, "value"));
    String comment = requireAttribute(specPath, reader, "comment");

    expectNoNestedElements(specPath, reader, "variant");
    return new ParsedBitVariant(name, value, comment);
  }

  private ParsedFloat parseFloat(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String sizeValue = requireAttribute(specPath, reader, "size");
    String encodingValue = requireAttribute(specPath, reader, "encoding");
    String comment = requireAttribute(specPath, reader, "comment");

    FloatSize size;
    try {
      size = FloatSize.fromXml(sizeValue);
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported float size value: " + sizeValue);
    }

    FloatEncoding encoding;
    try {
      encoding = FloatEncoding.fromXml(encodingValue);
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported float encoding value: " + encodingValue);
    }

    BigDecimal scale = null;
    String scaleValue = reader.getAttributeValue(null, "scale");
    if (scaleValue != null) {
      scale = parseDecimal(specPath, reader, "scale", scaleValue);
    }

    Endian endian = parseOptionalEndian(specPath, reader, "float");
    expectNoNestedElements(specPath, reader, "float");

    return new ParsedFloat(name, size, encoding, scale, endian, comment);
  }

  private ParsedScaledInt parseScaledInt(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String baseTypeName = requireAttribute(specPath, reader, "baseType");
    String scaleValue = requireAttribute(specPath, reader, "scale");
    String comment = requireAttribute(specPath, reader, "comment");

    BigDecimal scale = parseDecimal(specPath, reader, "scale", scaleValue);
    Endian endian = parseOptionalEndian(specPath, reader, "scaledInt");
    expectNoNestedElements(specPath, reader, "scaledInt");

    return new ParsedScaledInt(name, baseTypeName, scale, endian, comment);
  }

  private static void expectNoNestedElements(
      Path specPath, XMLStreamReader reader, String elementName)
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

  private static Endian parseOptionalEndian(Path specPath, XMLStreamReader reader, String context)
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

  private static int parseUnsignedByte(
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

  private static BigInteger parseUnsignedLong(
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

  private static BigDecimal parseDecimal(
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

  private record RootItems(
      List<ParsedMessageType> messageTypes,
      List<ParsedBitField> bitFields,
      List<ParsedFloat> floats,
      List<ParsedScaledInt> scaledInts) {}
}
