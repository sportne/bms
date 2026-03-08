package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
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
import java.util.Map;
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
 *   <li>{@code <array>}
 *   <li>{@code <vector>}
 *   <li>{@code <blobArray>}
 *   <li>{@code <blobVector>}
 *   <li>{@code <varString>}
 *   <li>{@code <pad>}
 *   <li>{@code <checksum>}
 *   <li>{@code <if>}
 *   <li>{@code <type>} (nested message)
 * </ul>
 *
 * <p>If an unsupported XML element appears, parsing fails fast with a clear diagnostic.
 */
public final class SpecParser {
  private static final String ROOT_ELEMENT = "schema";
  private static final BigInteger MAX_UNSIGNED_LONG =
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

  private final XMLInputFactory inputFactory;
  private final Map<String, RootElementHandler> rootElementHandlers;
  private final Map<String, MessageMemberHandler> messageMemberHandlers;

  /** Creates a parser and disables XML features that are not needed for BMS files. */
  public SpecParser() {
    inputFactory = XMLInputFactory.newFactory();
    inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    inputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    rootElementHandlers = createRootElementHandlers();
    messageMemberHandlers = createMessageMemberHandlers();
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
      throw singleDiagnosticException(
          "PARSER_EMPTY_DOCUMENT", "XML document has no root element.", specPath, -1, -1);
    }
  }

  /**
   * Parses all supported direct children under {@code <schema>}.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <schema>}
   * @return grouped root-level items
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if unsupported or malformed XML is found
   */
  private RootItems parseRootChildren(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    RootItemsBuilder rootItemsBuilder = new RootItemsBuilder();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        parseRootChild(specPath, reader, rootItemsBuilder);
      }
      if (event == XMLStreamConstants.END_ELEMENT && ROOT_ELEMENT.equals(reader.getLocalName())) {
        return rootItemsBuilder.build();
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <schema>.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses one supported child element under {@code <schema>} and adds it to the right list.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on a root child element
   * @param rootItemsBuilder destination accumulator for all root-level parsed definitions
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if the current root child is unsupported
   */
  private void parseRootChild(
      Path specPath, XMLStreamReader reader, RootItemsBuilder rootItemsBuilder)
      throws XMLStreamException, BmsException {
    RootElementHandler rootElementHandler = rootElementHandlers.get(reader.getLocalName());
    if (rootElementHandler == null) {
      throw unsupportedElement(specPath, reader, "root");
    }
    rootElementHandler.parse(specPath, reader, rootItemsBuilder);
  }

  /**
   * Parses one {@code <messageType>} element and preserves member order.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <messageType>}
   * @return parsed message type
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or child elements are invalid
   */
  private ParsedMessageType parseMessageType(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    return parseMessageTypeLike(specPath, reader, "messageType");
  }

  /**
   * Parses one nested {@code <type>} member.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <type>}
   * @return parsed nested message type
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or child elements are invalid
   */
  private ParsedMessageType parseTypeMember(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    return parseMessageTypeLike(specPath, reader, "type");
  }

  /**
   * Parses one message-like element ({@code messageType} or nested {@code type}).
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on the message-like element
   * @param elementName element name that terminates this parse call
   * @return parsed message object
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or child elements are invalid
   */
  private ParsedMessageType parseMessageTypeLike(
      Path specPath, XMLStreamReader reader, String elementName)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String comment = requireAttribute(specPath, reader, "comment");
    String namespaceOverride = reader.getAttributeValue(null, "namespace");

    List<ParsedMessageMember> members = parseMessageMembers(specPath, reader, elementName, true);
    return new ParsedMessageType(name, comment, namespaceOverride, members);
  }

  /**
   * Parses ordered message members under a parent that uses message-member choices.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on the parent element
   * @param parentName parent element that will end this parse call
   * @param allowIf whether nested {@code if} blocks are allowed in this parent
   * @return parsed members in XML declaration order
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if child elements are invalid or unsupported
   */
  private List<ParsedMessageMember> parseMessageMembers(
      Path specPath, XMLStreamReader reader, String parentName, boolean allowIf)
      throws XMLStreamException, BmsException {
    List<ParsedMessageMember> members = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        members.add(parseMessageMember(specPath, reader, parentName, allowIf));
      }
      if (event == XMLStreamConstants.END_ELEMENT && parentName.equals(reader.getLocalName())) {
        return members;
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <" + parentName + ">.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses one message member child from the current reader position.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on a member element
   * @param parentName parent element name used in diagnostics
   * @param allowIf whether nested {@code if} blocks are allowed in this parent
   * @return parsed message member
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if the member is invalid or unsupported
   */
  private ParsedMessageMember parseMessageMember(
      Path specPath, XMLStreamReader reader, String parentName, boolean allowIf)
      throws XMLStreamException, BmsException {
    if ("if".equals(reader.getLocalName())) {
      if (!allowIf) {
        throw parserError(
            specPath,
            reader,
            "PARSER_UNSUPPORTED_ELEMENT",
            "<" + parentName + "> does not support nested <if>.");
      }
      return parseIfBlock(specPath, reader);
    }

    MessageMemberHandler messageMemberHandler = messageMemberHandlers.get(reader.getLocalName());
    if (messageMemberHandler == null) {
      throw unsupportedElement(specPath, reader, parentName);
    }
    return messageMemberHandler.parse(specPath, reader);
  }

  /**
   * Builds parser handlers for root-level child elements.
   *
   * @return immutable map from root element name to parse handler
   */
  private Map<String, RootElementHandler> createRootElementHandlers() {
    return Map.ofEntries(
        Map.entry(
            "messageType",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.messageTypes.add(parseMessageType(specPath, reader))),
        Map.entry(
            "bitField",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.bitFields.add(parseBitField(specPath, reader))),
        Map.entry(
            "float",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.floats.add(parseFloat(specPath, reader))),
        Map.entry(
            "scaledInt",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.scaledInts.add(parseScaledInt(specPath, reader))),
        Map.entry(
            "array",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.arrays.add(parseArray(specPath, reader))),
        Map.entry(
            "vector",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.vectors.add(parseVector(specPath, reader))),
        Map.entry(
            "blobArray",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.blobArrays.add(parseBlobArray(specPath, reader))),
        Map.entry(
            "blobVector",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.blobVectors.add(parseBlobVector(specPath, reader))),
        Map.entry(
            "varString",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.varStrings.add(parseVarString(specPath, reader))),
        Map.entry(
            "checksum",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.checksums.add(parseChecksum(specPath, reader))),
        Map.entry(
            "pad",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.pads.add(parsePad(specPath, reader))));
  }

  /**
   * Builds parser handlers for message member elements.
   *
   * @return immutable map from member element name to parse handler
   */
  private Map<String, MessageMemberHandler> createMessageMemberHandlers() {
    return Map.ofEntries(
        Map.entry("field", this::parseField),
        Map.entry("bitField", this::parseBitField),
        Map.entry("float", this::parseFloat),
        Map.entry("scaledInt", this::parseScaledInt),
        Map.entry("array", this::parseArray),
        Map.entry("vector", this::parseVector),
        Map.entry("blobArray", this::parseBlobArray),
        Map.entry("blobVector", this::parseBlobVector),
        Map.entry("varString", this::parseVarString),
        Map.entry("checksum", this::parseChecksum),
        Map.entry("pad", this::parsePad),
        Map.entry("type", this::parseTypeMember));
  }

  /**
   * Parses one {@code <field>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <field>}
   * @return parsed field
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if a required attribute is missing or invalid
   */
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

  /**
   * Parses one {@code <bitField>} element and its nested members.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <bitField>}
   * @return parsed bitfield
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or nested elements are invalid
   */
  private ParsedBitField parseBitField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
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
        return new ParsedBitField(name, size, endian, comment, flags, segments);
      }
    }

    throw singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <bitField>.",
        specPath,
        -1,
        -1);
  }

  /**
   * Parses one {@code <flag>} entry under a bitfield.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <flag>}
   * @return parsed flag
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
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

  /**
   * Parses one {@code <segment>} entry under a bitfield.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <segment>}
   * @return parsed segment
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or nested variants are invalid
   */
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

  /**
   * Parses one {@code <variant>} entry under a segment.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <variant>}
   * @return parsed variant
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedBitVariant parseBitVariant(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    BigInteger value =
        parseUnsignedLong(specPath, reader, "value", requireAttribute(specPath, reader, "value"));
    String comment = requireAttribute(specPath, reader, "comment");

    expectNoNestedElements(specPath, reader, "variant");
    return new ParsedBitVariant(name, value, comment);
  }

  /**
   * Parses one {@code <float>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <float>}
   * @return parsed float
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
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

  /**
   * Parses one {@code <scaledInt>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <scaledInt>}
   * @return parsed scaled-int
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
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

  /**
   * Parses one {@code <array>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <array>}
   * @return parsed array
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedArray parseArray(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String elementTypeName =
        normalizeQNameLiteral(requireAttribute(specPath, reader, "elementType"));
    int length =
        parsePositiveInteger(
            specPath, reader, "length", requireAttribute(specPath, reader, "length"));
    Endian endian = parseOptionalEndian(specPath, reader, "array");
    String comment = requireAttribute(specPath, reader, "comment");

    expectNoNestedElements(specPath, reader, "array");
    return new ParsedArray(name, elementTypeName, length, endian, comment);
  }

  /**
   * Parses one {@code <vector>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <vector>}
   * @return parsed vector
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or nested length-mode children are invalid
   */
  private ParsedVector parseVector(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String elementTypeName =
        normalizeQNameLiteral(requireAttribute(specPath, reader, "elementType"));
    Endian endian = parseOptionalEndian(specPath, reader, "vector");
    String comment = requireAttribute(specPath, reader, "comment");
    ParsedLengthMode lengthMode = parseLengthMode(specPath, reader, "vector", true);
    return new ParsedVector(name, elementTypeName, endian, comment, lengthMode);
  }

  /**
   * Parses one {@code <blobArray>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <blobArray>}
   * @return parsed blob-array
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedBlobArray parseBlobArray(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    int length =
        parsePositiveInteger(
            specPath, reader, "length", requireAttribute(specPath, reader, "length"));
    String comment = requireAttribute(specPath, reader, "comment");

    expectNoNestedElements(specPath, reader, "blobArray");
    return new ParsedBlobArray(name, length, comment);
  }

  /**
   * Parses one {@code <blobVector>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <blobVector>}
   * @return parsed blob-vector
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or nested length-mode children are invalid
   */
  private ParsedBlobVector parseBlobVector(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String comment = requireAttribute(specPath, reader, "comment");
    ParsedLengthMode lengthMode = parseLengthMode(specPath, reader, "blobVector", false);
    return new ParsedBlobVector(name, comment, lengthMode);
  }

  /**
   * Parses one {@code <varString>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <varString>}
   * @return parsed varString
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or nested length-mode children are invalid
   */
  private ParsedVarString parseVarString(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = requireAttribute(specPath, reader, "name");
    String encodingValue = requireAttribute(specPath, reader, "encoding");
    String comment = requireAttribute(specPath, reader, "comment");
    StringEncoding encoding;
    try {
      encoding = StringEncoding.fromXml(encodingValue);
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported varString encoding value: " + encodingValue);
    }

    ParsedLengthMode lengthMode = parseLengthMode(specPath, reader, "varString", false);
    return new ParsedVarString(name, encoding, comment, lengthMode);
  }

  /**
   * Parses one {@code <checksum>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <checksum>}
   * @return parsed checksum
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if required attributes are missing
   */
  private ParsedChecksum parseChecksum(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String algorithm = requireAttribute(specPath, reader, "alg");
    String range = requireAttribute(specPath, reader, "range");
    String comment = requireAttribute(specPath, reader, "comment");
    expectNoNestedElements(specPath, reader, "checksum");
    return new ParsedChecksum(algorithm, range, comment);
  }

  /**
   * Parses one {@code <pad>} element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <pad>}
   * @return parsed pad
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes are missing or invalid
   */
  private ParsedPad parsePad(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    int bytes =
        parsePositiveInteger(
            specPath, reader, "bytes", requireAttribute(specPath, reader, "bytes"));
    String comment = reader.getAttributeValue(null, "comment");
    expectNoNestedElements(specPath, reader, "pad");
    return new ParsedPad(bytes, comment);
  }

  /**
   * Parses one {@code <if>} block and the members inside it.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @return parsed conditional block
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if attributes or child elements are invalid
   */
  private ParsedIfBlock parseIfBlock(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    IfAttributes ifAttributes = readIfAttributes(reader);
    String test = normalizeIfConditionText(specPath, reader, ifAttributes);

    List<ParsedMessageMember> members = parseMessageMembers(specPath, reader, "if", false);
    return new ParsedIfBlock(test, members);
  }

  /**
   * Reads all condition-related attributes from one {@code <if>} element.
   *
   * @param reader active XML reader positioned on {@code <if>}
   * @return immutable attribute bundle
   */
  private static IfAttributes readIfAttributes(XMLStreamReader reader) {
    return new IfAttributes(
        reader.getAttributeValue(null, "test"),
        reader.getAttributeValue(null, "field"),
        reader.getAttributeValue(null, "operator"),
        reader.getAttributeValue(null, "value"),
        reader.getAttributeValue(null, "logicalOperator"),
        reader.getAttributeValue(null, "field2"),
        reader.getAttributeValue(null, "operator2"),
        reader.getAttributeValue(null, "value2"));
  }

  /**
   * Validates and canonicalizes one parsed {@code <if>} condition.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @return canonical {@code if@test} text consumed by semantic parsing
   * @throws BmsException if attributes are missing or conflicting
   */
  private String normalizeIfConditionText(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.test() != null) {
      validateNoStructuredIfAttributes(specPath, reader, ifAttributes);
      return ifAttributes.test();
    }
    return normalizeStructuredIfConditionText(specPath, reader, ifAttributes);
  }

  /**
   * Validates that text-style conditions are not mixed with structured attributes.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if structured attributes are present
   */
  private void validateNoStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.hasAnyStructuredAttributes()) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "<if> must use either test=\"...\" or structured comparison attributes, not both.");
    }
  }

  /**
   * Validates and canonicalizes one structured if condition (simple or compound).
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @return canonical condition text
   * @throws BmsException if required structured attributes are missing
   */
  private String normalizeStructuredIfConditionText(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    validatePrimaryStructuredIfAttributes(specPath, reader, ifAttributes);
    String firstComparison =
        ifAttributes.field()
            + " "
            + parseIfComparisonOperatorSymbol(specPath, reader, ifAttributes.operator())
            + " "
            + ifAttributes.value();
    if (!ifAttributes.hasCompoundStructuredAttributes()) {
      return firstComparison;
    }
    validateCompoundStructuredIfAttributes(specPath, reader, ifAttributes);
    return firstComparison
        + " "
        + parseIfLogicalOperatorToken(specPath, reader, ifAttributes.logicalOperator())
        + " "
        + ifAttributes.field2()
        + " "
        + parseIfComparisonOperatorSymbol(specPath, reader, ifAttributes.operator2())
        + " "
        + ifAttributes.value2();
  }

  /**
   * Validates required fields for a primary structured comparison.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if any required primary attribute is missing
   */
  private void validatePrimaryStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.field() == null
        || ifAttributes.operator() == null
        || ifAttributes.value() == null) {
      throw parserError(
          specPath,
          reader,
          "PARSER_MISSING_ATTRIBUTE",
          "<if> requires either test=\"...\" or structured comparison attributes.");
    }
  }

  /**
   * Validates required fields for a compound structured comparison.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if any required compound attribute is missing
   */
  private void validateCompoundStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.logicalOperator() == null
        || ifAttributes.field2() == null
        || ifAttributes.operator2() == null
        || ifAttributes.value2() == null) {
      throw parserError(
          specPath,
          reader,
          "PARSER_MISSING_ATTRIBUTE",
          "<if> compound structured comparisons require logicalOperator, field2, operator2, and value2.");
    }
  }

  /**
   * Converts one enum-style if-comparison operator to canonical symbol text.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param operatorValue enum operator literal from XML
   * @return canonical comparison symbol used by downstream stages
   * @throws BmsException if the operator value is unsupported
   */
  private String parseIfComparisonOperatorSymbol(
      Path specPath, XMLStreamReader reader, String operatorValue) throws BmsException {
    try {
      return IfComparisonOperator.fromXmlToken(operatorValue).symbol();
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported if comparison operator value: " + operatorValue);
    }
  }

  /**
   * Converts one enum-style logical operator to canonical text used in {@code if@test}.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param operatorValue enum logical operator literal from XML
   * @return canonical logical text used by semantic parsing
   * @throws BmsException if the logical-operator value is unsupported
   */
  private String parseIfLogicalOperatorToken(
      Path specPath, XMLStreamReader reader, String operatorValue) throws BmsException {
    try {
      return IfLogicalOperator.fromXmlToken(operatorValue).xmlToken();
    } catch (IllegalArgumentException exception) {
      throw parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported if logical operator value: " + operatorValue);
    }
  }

  /**
   * Parsed attribute bundle for one {@code <if>} condition declaration.
   *
   * @param test text condition value
   * @param field first comparison field name
   * @param operator first comparison operator token
   * @param value first comparison literal
   * @param logicalOperator logical operator token between comparisons
   * @param field2 second comparison field name
   * @param operator2 second comparison operator token
   * @param value2 second comparison literal
   */
  private record IfAttributes(
      String test,
      String field,
      String operator,
      String value,
      String logicalOperator,
      String field2,
      String operator2,
      String value2) {

    /**
     * Returns whether any structured condition attribute is present.
     *
     * @return {@code true} when any structured attribute is present
     */
    private boolean hasAnyStructuredAttributes() {
      return field != null
          || operator != null
          || value != null
          || logicalOperator != null
          || field2 != null
          || operator2 != null
          || value2 != null;
    }

    /**
     * Returns whether any compound-structured condition attribute is present.
     *
     * @return {@code true} when any compound attribute is present
     */
    private boolean hasCompoundStructuredAttributes() {
      return logicalOperator != null || field2 != null || operator2 != null || value2 != null;
    }
  }

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
  private ParsedLengthMode parseLengthMode(
      Path specPath, XMLStreamReader reader, String parentName, boolean allowTerminatorField)
      throws XMLStreamException, BmsException {
    ParsedLengthMode lengthMode = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (lengthMode != null) {
          throw parserError(
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
                  throw parserError(
                      specPath,
                      reader,
                      "PARSER_UNSUPPORTED_ELEMENT",
                      "<" + parentName + "> does not support <terminatorField>.");
                }
                yield parseTerminatorField(specPath, reader);
              }
              default -> throw parserError(
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
          throw parserError(
              specPath,
              reader,
              "PARSER_MISSING_CHILD_ELEMENT",
              "<" + parentName + "> requires one length mode child.");
        }
        return lengthMode;
      }
    }

    throw singleDiagnosticException(
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
    String ref = requireAttribute(specPath, reader, "ref");
    expectNoNestedElements(specPath, reader, "countField");
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
    String value = requireAttribute(specPath, reader, "value");
    expectNoNestedElements(specPath, reader, "terminatorValue");
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
    String name = requireAttribute(specPath, reader, "name");
    ParsedTerminatorNode next = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (next != null) {
          throw parserError(
              specPath,
              reader,
              "PARSER_INVALID_FIELD_CONTENT",
              "<terminatorField> supports at most one nested node.");
        }
        next =
            switch (reader.getLocalName()) {
              case "terminatorField" -> parseTerminatorField(specPath, reader);
              case "terminatorMatch" -> parseTerminatorMatch(specPath, reader);
              default -> throw parserError(
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

    throw singleDiagnosticException(
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
    String value = requireAttribute(specPath, reader, "value");
    expectNoNestedElements(specPath, reader, "terminatorMatch");
    return new ParsedTerminatorMatch(value);
  }

  /**
   * Ensures the current element has no nested start elements.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param elementName element expected to close next
   * @throws XMLStreamException if XML streaming fails
   * @throws BmsException if a nested element is found
   */
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

  /**
   * Parses an optional {@code endian} attribute.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param context element name used in diagnostic text
   * @return parsed endian value, or {@code null} when not present
   * @throws BmsException if the attribute value is invalid
   */
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

  /**
   * Normalizes a QName-like string by removing any namespace prefix.
   *
   * @param qNameLiteral raw QName-like literal from XML
   * @return local-name portion without prefix
   */
  private static String normalizeQNameLiteral(String qNameLiteral) {
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

  /**
   * Creates an exception for an unsupported child element.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param parentContext parent element name
   * @return exception with one diagnostic
   */
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

  /**
   * Creates an exception at the reader's current location.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader
   * @param code stable diagnostic code
   * @param message human-readable diagnostic message
   * @return exception with one diagnostic
   */
  private static BmsException parserError(
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
  private static BmsException singleDiagnosticException(
      String code, String message, Path specPath, int line, int column) {
    Diagnostic diagnostic =
        new Diagnostic(DiagnosticSeverity.ERROR, code, message, specPath.toString(), line, column);
    return new BmsException(message, List.of(diagnostic));
  }

  /** Parse callback for one root-level element. */
  @FunctionalInterface
  private interface RootElementHandler {
    /**
     * Parses one root child element.
     *
     * @param specPath source file path used in diagnostics
     * @param reader active XML reader
     * @param rootItemsBuilder destination accumulator for root-level parsed items
     * @throws XMLStreamException if XML streaming fails
     * @throws BmsException if parsing fails
     */
    void parse(Path specPath, XMLStreamReader reader, RootItemsBuilder rootItemsBuilder)
        throws XMLStreamException, BmsException;
  }

  /** Parse callback for one message member element. */
  @FunctionalInterface
  private interface MessageMemberHandler {
    /**
     * Parses one message member element.
     *
     * @param specPath source file path used in diagnostics
     * @param reader active XML reader
     * @return parsed message member
     * @throws XMLStreamException if XML streaming fails
     * @throws BmsException if parsing fails
     */
    ParsedMessageMember parse(Path specPath, XMLStreamReader reader)
        throws XMLStreamException, BmsException;
  }

  /** Mutable root-item accumulator used while scanning schema children. */
  private static final class RootItemsBuilder {
    private final List<ParsedMessageType> messageTypes = new ArrayList<>();
    private final List<ParsedBitField> bitFields = new ArrayList<>();
    private final List<ParsedFloat> floats = new ArrayList<>();
    private final List<ParsedScaledInt> scaledInts = new ArrayList<>();
    private final List<ParsedArray> arrays = new ArrayList<>();
    private final List<ParsedVector> vectors = new ArrayList<>();
    private final List<ParsedBlobArray> blobArrays = new ArrayList<>();
    private final List<ParsedBlobVector> blobVectors = new ArrayList<>();
    private final List<ParsedVarString> varStrings = new ArrayList<>();
    private final List<ParsedChecksum> checksums = new ArrayList<>();
    private final List<ParsedPad> pads = new ArrayList<>();

    /** Creates one mutable root-item accumulator. */
    private RootItemsBuilder() {}

    /**
     * Builds immutable root-item groups from accumulated values.
     *
     * @return immutable grouped root items
     */
    private RootItems build() {
      return new RootItems(
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
    }
  }

  private record RootItems(
      List<ParsedMessageType> messageTypes,
      List<ParsedBitField> bitFields,
      List<ParsedFloat> floats,
      List<ParsedScaledInt> scaledInts,
      List<ParsedArray> arrays,
      List<ParsedVector> vectors,
      List<ParsedBlobArray> blobArrays,
      List<ParsedBlobVector> blobVectors,
      List<ParsedVarString> varStrings,
      List<ParsedChecksum> checksums,
      List<ParsedPad> pads) {}
}
