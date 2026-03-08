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
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedTerminatorNode;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses message-level and reusable schema elements. */
final class MessageElementParser {
  private final Map<String, MessageMemberHandler> messageMemberHandlers;

  /** Creates one message-element parser with static member handlers. */
  MessageElementParser() {
    messageMemberHandlers = createMessageMemberHandlers();
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
  ParsedMessageType parseMessageType(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    return parseMessageTypeLike(specPath, reader, "messageType");
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
  ParsedField parseField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String rawTypeName = ParserSupport.requireAttribute(specPath, reader, "type");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    String normalizedTypeName = ParserSupport.normalizeQNameLiteral(rawTypeName);

    String lengthValue = reader.getAttributeValue(null, "length");
    Integer length =
        lengthValue == null
            ? null
            : ParserSupport.parsePositiveInteger(specPath, reader, "length", lengthValue);

    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "field");
    String fixed = reader.getAttributeValue(null, "fixed");

    ParserSupport.expectNoNestedElements(specPath, reader, "field");
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
  ParsedBitField parseBitField(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String rawSize = ParserSupport.requireAttribute(specPath, reader, "size");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    BitFieldSize size;
    try {
      size = BitFieldSize.fromXml(rawSize);
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported bitField size value: " + rawSize);
    }

    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "bitField");
    List<ParsedBitFlag> flags = new ArrayList<>();
    List<ParsedBitSegment> segments = new ArrayList<>();

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        switch (reader.getLocalName()) {
          case "flag" -> flags.add(parseBitFlag(specPath, reader));
          case "segment" -> segments.add(parseBitSegment(specPath, reader));
          default -> throw ParserSupport.parserError(
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

    throw ParserSupport.singleDiagnosticException(
        "PARSER_UNEXPECTED_EOF",
        "Unexpected end of file while reading <bitField>.",
        specPath,
        -1,
        -1);
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
  ParsedFloat parseFloat(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String sizeValue = ParserSupport.requireAttribute(specPath, reader, "size");
    String encodingValue = ParserSupport.requireAttribute(specPath, reader, "encoding");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    FloatSize size;
    try {
      size = FloatSize.fromXml(sizeValue);
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported float size value: " + sizeValue);
    }

    FloatEncoding encoding;
    try {
      encoding = FloatEncoding.fromXml(encodingValue);
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported float encoding value: " + encodingValue);
    }

    BigDecimal scale = null;
    String scaleValue = reader.getAttributeValue(null, "scale");
    if (scaleValue != null) {
      scale = ParserSupport.parseDecimal(specPath, reader, "scale", scaleValue);
    }

    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "float");
    ParserSupport.expectNoNestedElements(specPath, reader, "float");
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
  ParsedScaledInt parseScaledInt(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String baseTypeName = ParserSupport.requireAttribute(specPath, reader, "baseType");
    String scaleValue = ParserSupport.requireAttribute(specPath, reader, "scale");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    BigDecimal scale = ParserSupport.parseDecimal(specPath, reader, "scale", scaleValue);
    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "scaledInt");
    ParserSupport.expectNoNestedElements(specPath, reader, "scaledInt");
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
  ParsedArray parseArray(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String elementTypeName =
        ParserSupport.normalizeQNameLiteral(
            ParserSupport.requireAttribute(specPath, reader, "elementType"));
    int length =
        ParserSupport.parsePositiveInteger(
            specPath, reader, "length", ParserSupport.requireAttribute(specPath, reader, "length"));
    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "array");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    ParserSupport.expectNoNestedElements(specPath, reader, "array");
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
  ParsedVector parseVector(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String elementTypeName =
        ParserSupport.normalizeQNameLiteral(
            ParserSupport.requireAttribute(specPath, reader, "elementType"));
    Endian endian = ParserSupport.parseOptionalEndian(specPath, reader, "vector");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
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
  ParsedBlobArray parseBlobArray(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    int length =
        ParserSupport.parsePositiveInteger(
            specPath, reader, "length", ParserSupport.requireAttribute(specPath, reader, "length"));
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    ParserSupport.expectNoNestedElements(specPath, reader, "blobArray");
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
  ParsedBlobVector parseBlobVector(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
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
  ParsedVarString parseVarString(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String encodingValue = ParserSupport.requireAttribute(specPath, reader, "encoding");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
    StringEncoding encoding;
    try {
      encoding = StringEncoding.fromXml(encodingValue);
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
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
  ParsedChecksum parseChecksum(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    String algorithm = ParserSupport.requireAttribute(specPath, reader, "alg");
    String range = ParserSupport.requireAttribute(specPath, reader, "range");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
    ParserSupport.expectNoNestedElements(specPath, reader, "checksum");
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
  ParsedPad parsePad(Path specPath, XMLStreamReader reader)
      throws XMLStreamException, BmsException {
    int bytes =
        ParserSupport.parsePositiveInteger(
            specPath, reader, "bytes", ParserSupport.requireAttribute(specPath, reader, "bytes"));
    String comment = reader.getAttributeValue(null, "comment");
    ParserSupport.expectNoNestedElements(specPath, reader, "pad");
    return new ParsedPad(bytes, comment);
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

    throw ParserSupport.singleDiagnosticException(
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
        throw ParserSupport.parserError(
            specPath,
            reader,
            "PARSER_UNSUPPORTED_ELEMENT",
            "<" + parentName + "> does not support nested <if>.");
      }
      return parseIfBlock(specPath, reader);
    }

    MessageMemberHandler handler = messageMemberHandlers.get(reader.getLocalName());
    if (handler == null) {
      throw ParserSupport.unsupportedElement(specPath, reader, parentName);
    }
    return handler.parse(specPath, reader);
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
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
    String namespaceOverride = reader.getAttributeValue(null, "namespace");
    List<ParsedMessageMember> members = parseMessageMembers(specPath, reader, elementName, true);
    return new ParsedMessageType(name, comment, namespaceOverride, members);
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
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    int position =
        ParserSupport.parseUnsignedByte(
            specPath,
            reader,
            "position",
            ParserSupport.requireAttribute(specPath, reader, "position"));
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
    ParserSupport.expectNoNestedElements(specPath, reader, "flag");
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
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    int from =
        ParserSupport.parseUnsignedByte(
            specPath, reader, "from", ParserSupport.requireAttribute(specPath, reader, "from"));
    int to =
        ParserSupport.parseUnsignedByte(
            specPath, reader, "to", ParserSupport.requireAttribute(specPath, reader, "to"));
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");

    List<ParsedBitVariant> variants = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        if (!"variant".equals(reader.getLocalName())) {
          throw ParserSupport.parserError(
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

    throw ParserSupport.singleDiagnosticException(
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
    String name = ParserSupport.requireAttribute(specPath, reader, "name");
    BigInteger value =
        ParserSupport.parseUnsignedLong(
            specPath, reader, "value", ParserSupport.requireAttribute(specPath, reader, "value"));
    String comment = ParserSupport.requireAttribute(specPath, reader, "comment");
    ParserSupport.expectNoNestedElements(specPath, reader, "variant");
    return new ParsedBitVariant(name, value, comment);
  }

  /**
   * Parses one {@code <if>} block and members inside it.
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
      throw ParserSupport.parserError(
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
      throw ParserSupport.parserError(
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
      throw ParserSupport.parserError(
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
      throw ParserSupport.parserError(
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
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported if logical operator value: " + operatorValue);
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
}
