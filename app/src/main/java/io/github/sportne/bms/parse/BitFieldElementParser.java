package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.util.BmsException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses {@code bitField} members and their nested children. */
final class BitFieldElementParser {

  /** Creates one bit-field parser instance. */
  BitFieldElementParser() {}

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
}
