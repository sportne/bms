package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedMessageMember;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses message-level and reusable schema elements. */
final class MessageElementParser {
  private final BitFieldElementParser bitFieldElementParser;
  private final ScalarElementParser scalarElementParser;
  private final CollectionElementParser collectionElementParser;
  private final ConditionalAttributesParser conditionalAttributesParser;
  private final Map<String, MessageMemberHandler> messageMemberHandlers;

  /** Creates one message-element parser with static member handlers. */
  MessageElementParser() {
    LengthModeParser lengthModeParser = new LengthModeParser();
    bitFieldElementParser = new BitFieldElementParser();
    scalarElementParser = new ScalarElementParser(lengthModeParser);
    collectionElementParser = new CollectionElementParser(lengthModeParser);
    conditionalAttributesParser = new ConditionalAttributesParser();
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
    return scalarElementParser.parseField(specPath, reader);
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
    return bitFieldElementParser.parseBitField(specPath, reader);
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
    return scalarElementParser.parseFloat(specPath, reader);
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
    return scalarElementParser.parseScaledInt(specPath, reader);
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
    return collectionElementParser.parseArray(specPath, reader);
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
    return collectionElementParser.parseVector(specPath, reader);
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
    return collectionElementParser.parseBlobArray(specPath, reader);
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
    return collectionElementParser.parseBlobVector(specPath, reader);
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
    return scalarElementParser.parseVarString(specPath, reader);
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
    return scalarElementParser.parseChecksum(specPath, reader);
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
    return scalarElementParser.parsePad(specPath, reader);
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
    String test = conditionalAttributesParser.normalizeIfConditionText(specPath, reader);
    List<ParsedMessageMember> members = parseMessageMembers(specPath, reader, "if", false);
    return new ParsedIfBlock(test, members);
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
}
