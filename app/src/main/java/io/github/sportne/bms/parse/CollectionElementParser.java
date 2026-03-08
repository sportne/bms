package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Parses collection members and reusable collection definitions. */
final class CollectionElementParser {
  private final LengthModeParser lengthModeParser;

  /**
   * Creates one collection-element parser.
   *
   * @param lengthModeParser collaborator that parses collection length modes
   */
  CollectionElementParser(LengthModeParser lengthModeParser) {
    this.lengthModeParser = lengthModeParser;
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
    ParsedLengthMode lengthMode =
        lengthModeParser.parseLengthMode(specPath, reader, "vector", true);
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
    ParsedLengthMode lengthMode =
        lengthModeParser.parseLengthMode(specPath, reader, "blobVector", false);
    return new ParsedBlobVector(name, comment, lengthMode);
  }
}
