package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedFloat;
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

/** Parses root-level children under {@code <schema>}. */
final class RootElementParser {
  private static final String ROOT_ELEMENT = "schema";

  private final MessageElementParser messageElementParser;
  private final Map<String, RootElementHandler> rootElementHandlers;

  /**
   * Creates one root-element parser bound to one message-element parser.
   *
   * @param messageElementParser collaborator that parses individual element payloads
   */
  RootElementParser(MessageElementParser messageElementParser) {
    this.messageElementParser = messageElementParser;
    rootElementHandlers = createRootElementHandlers();
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
  RootItems parseRootChildren(Path specPath, XMLStreamReader reader)
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

    throw ParserSupport.singleDiagnosticException(
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
      throw ParserSupport.unsupportedElement(specPath, reader, "root");
    }
    rootElementHandler.parse(specPath, reader, rootItemsBuilder);
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
                rootItemsBuilder.messageTypes.add(
                    messageElementParser.parseMessageType(specPath, reader))),
        Map.entry(
            "bitField",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.bitFields.add(
                    messageElementParser.parseBitField(specPath, reader))),
        Map.entry(
            "float",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.floats.add(messageElementParser.parseFloat(specPath, reader))),
        Map.entry(
            "scaledInt",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.scaledInts.add(
                    messageElementParser.parseScaledInt(specPath, reader))),
        Map.entry(
            "array",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.arrays.add(messageElementParser.parseArray(specPath, reader))),
        Map.entry(
            "vector",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.vectors.add(messageElementParser.parseVector(specPath, reader))),
        Map.entry(
            "blobArray",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.blobArrays.add(
                    messageElementParser.parseBlobArray(specPath, reader))),
        Map.entry(
            "blobVector",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.blobVectors.add(
                    messageElementParser.parseBlobVector(specPath, reader))),
        Map.entry(
            "varString",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.varStrings.add(
                    messageElementParser.parseVarString(specPath, reader))),
        Map.entry(
            "checksum",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.checksums.add(
                    messageElementParser.parseChecksum(specPath, reader))),
        Map.entry(
            "pad",
            (specPath, reader, rootItemsBuilder) ->
                rootItemsBuilder.pads.add(messageElementParser.parsePad(specPath, reader))));
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

  /**
   * Grouped root-level parse output.
   *
   * @param messageTypes parsed root message types
   * @param bitFields parsed reusable bitfields
   * @param floats parsed reusable floats
   * @param scaledInts parsed reusable scaled-ints
   * @param arrays parsed reusable arrays
   * @param vectors parsed reusable vectors
   * @param blobArrays parsed reusable blob arrays
   * @param blobVectors parsed reusable blob vectors
   * @param varStrings parsed reusable varStrings
   * @param checksums parsed reusable checksums
   * @param pads parsed reusable pads
   */
  record RootItems(
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
