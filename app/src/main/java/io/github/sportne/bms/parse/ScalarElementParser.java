package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.StringEncoding;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses scalar value members and simple fixed-shape elements. */
final class ScalarElementParser {
  private final LengthModeParser lengthModeParser;

  /**
   * Creates one scalar-element parser.
   *
   * @param lengthModeParser collaborator for varString length-mode parsing
   */
  ScalarElementParser(LengthModeParser lengthModeParser) {
    this.lengthModeParser = lengthModeParser;
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
    ParsedLengthMode lengthMode =
        lengthModeParser.parseLengthMode(specPath, reader, "varString", false);
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
}
