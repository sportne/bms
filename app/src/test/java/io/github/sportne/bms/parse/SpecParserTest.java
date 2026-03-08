package io.github.sportne.bms.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedChecksum;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedIfBlock;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedPad;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorMatch;
import io.github.sportne.bms.model.parsed.ParsedVarString;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the StAX parser.
 *
 * <p>These tests answer two questions:
 *
 * <ul>
 *   <li>Which XML constructs are currently supported?
 *   <li>How should unsupported or malformed input fail?
 * </ul>
 */
class SpecParserTest {

  /** Contract: parser keeps namespace values and message field order from XML. */
  @Test
  void parserReadsSchemaNamespaceMessageOverrideAndFieldOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/valid-foundation.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry", parsedSchema.namespace());
    assertEquals(2, parsedSchema.messageTypes().size());

    var header = parsedSchema.messageTypes().get(0);
    assertEquals("Header", header.name());
    assertEquals(2, header.fields().size());
    assertEquals("version", header.fields().get(0).name());
    assertEquals("sequence", header.fields().get(1).name());

    var packet = parsedSchema.messageTypes().get(1);
    assertEquals("acme.telemetry.packet", packet.namespaceOverride());
  }

  /** Contract: unsupported root elements fail fast with a parser diagnostic. */
  @Test
  void parserFailsFastForUnsupportedElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-root-unknown.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: unsupported nested message members fail fast with clear context. */
  @Test
  void parserFailsFastForUnsupportedMessageTypeElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-message-unknown.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")
                        && diagnostic.message().contains("<messageType>")));
  }

  /** Contract: numeric slice elements parse successfully and preserve mixed member order. */
  @Test
  void parserReadsNumericSliceAndPreservesMemberOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/numeric-slice-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry.numeric", parsedSchema.namespace());
    assertEquals(1, parsedSchema.reusableBitFields().size());
    assertEquals(1, parsedSchema.reusableFloats().size());
    assertEquals(1, parsedSchema.reusableScaledInts().size());
    assertEquals("statusWord", parsedSchema.reusableBitFields().get(0).name());
    assertEquals("TelemetryFloat", parsedSchema.reusableFloats().get(0).name());

    var message = parsedSchema.messageTypes().get(0);
    assertEquals("TelemetryFrame", message.name());
    assertEquals(6, message.members().size());
    assertEquals("version", message.fields().get(0).name());
    assertTrue(message.members().get(1) instanceof ParsedBitField);
    assertEquals("statusBits", ((ParsedBitField) message.members().get(1)).name());
    assertEquals("temperature", ((ParsedFloat) message.members().get(2)).name());
    assertEquals(FloatEncoding.SCALED, ((ParsedFloat) message.members().get(2)).encoding());
    assertTrue(message.members().get(3) instanceof ParsedScaledInt);
    assertEquals("reusableTemperature", message.fields().get(1).name());
    assertEquals("reusableFloat", message.fields().get(2).name());
  }

  /** Contract: collection slice elements parse successfully and keep mixed member order. */
  @Test
  void parserReadsCollectionSliceAndPreservesMemberOrder() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/collections-slice-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals("acme.telemetry.collections", parsedSchema.namespace());
    assertEquals(1, parsedSchema.reusableArrays().size());
    assertEquals(2, parsedSchema.reusableVectors().size());
    assertEquals(1, parsedSchema.reusableBlobArrays().size());
    assertEquals(1, parsedSchema.reusableBlobVectors().size());

    assertEquals("BytePair", parsedSchema.reusableArrays().get(0).name());
    assertEquals("ByteStream", parsedSchema.reusableVectors().get(0).name());
    assertEquals("Signature", parsedSchema.reusableBlobArrays().get(0).name());
    assertEquals("PayloadBlob", parsedSchema.reusableBlobVectors().get(0).name());

    var message = parsedSchema.messageTypes().get(0);
    assertEquals("CollectionFrame", message.name());
    assertEquals(11, message.members().size());

    assertEquals("count", message.fields().get(0).name());
    assertEquals("blobCount", message.fields().get(1).name());

    assertTrue(message.members().get(2) instanceof ParsedArray);
    assertEquals("samples", ((ParsedArray) message.members().get(2)).name());

    assertTrue(message.members().get(3) instanceof ParsedVector);
    ParsedVector countedVector = (ParsedVector) message.members().get(3);
    assertEquals("events", countedVector.name());
    assertTrue(countedVector.lengthMode() instanceof ParsedCountFieldLength);
    assertEquals("count", ((ParsedCountFieldLength) countedVector.lengthMode()).ref());

    assertTrue(message.members().get(4) instanceof ParsedBlobArray);
    assertEquals("hash", ((ParsedBlobArray) message.members().get(4)).name());

    assertTrue(message.members().get(5) instanceof ParsedBlobVector);
    ParsedBlobVector countedBlobVector = (ParsedBlobVector) message.members().get(5);
    assertEquals("payload", countedBlobVector.name());
    assertTrue(countedBlobVector.lengthMode() instanceof ParsedCountFieldLength);
    assertEquals("blobCount", ((ParsedCountFieldLength) countedBlobVector.lengthMode()).ref());

    assertTrue(message.members().get(6) instanceof ParsedVector);
    ParsedVector terminatorPathVector = (ParsedVector) message.members().get(6);
    assertEquals("pathData", terminatorPathVector.name());
    assertTrue(terminatorPathVector.lengthMode() instanceof ParsedTerminatorField);
    ParsedTerminatorField firstNode = (ParsedTerminatorField) terminatorPathVector.lengthMode();
    assertEquals("outer", firstNode.name());
    assertTrue(firstNode.next() instanceof ParsedTerminatorField);
    ParsedTerminatorField secondNode = (ParsedTerminatorField) firstNode.next();
    assertEquals("inner", secondNode.name());
    assertTrue(secondNode.next() instanceof ParsedTerminatorMatch);
    assertEquals("0x00", ((ParsedTerminatorMatch) secondNode.next()).value());
  }

  /** Contract: milestone-03 members parse correctly in both root and message scopes. */
  @Test
  void parserReadsMilestoneThreeSlice() throws Exception {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/milestone-03-valid.xml");

    ParsedSchema parsedSchema = parser.parse(specPath);

    assertEquals(1, parsedSchema.reusableVarStrings().size());
    assertEquals(1, parsedSchema.reusableChecksums().size());
    assertEquals(1, parsedSchema.reusablePads().size());
    assertEquals("LabelText", parsedSchema.reusableVarStrings().get(0).name());
    assertEquals("crc32", parsedSchema.reusableChecksums().get(0).algorithm());
    assertEquals(2, parsedSchema.reusablePads().get(0).bytes());

    var message = parsedSchema.messageTypes().get(0);
    assertEquals(8, message.members().size());
    assertTrue(message.members().get(2) instanceof ParsedVarString);
    assertTrue(message.members().get(4) instanceof ParsedPad);
    assertTrue(message.members().get(5) instanceof ParsedChecksum);
    assertTrue(message.members().get(6) instanceof ParsedIfBlock);
    assertTrue(message.members().get(7) instanceof ParsedMessageType);

    ParsedIfBlock ifBlock = (ParsedIfBlock) message.members().get(6);
    assertEquals("version == 1", ifBlock.test());
    assertEquals(3, ifBlock.members().size());
    assertTrue(ifBlock.members().get(1) instanceof ParsedVarString);
  }

  /** Contract: an `if` block cannot mix legacy `test` and structured comparison attributes. */
  @Test
  void parserRejectsMixedIfConditionAttributeStyles() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/if-invalid-attribute-combination.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: structured if comparisons must include `field`, `operator`, and `value`. */
  @Test
  void parserRejectsMissingStructuredIfComparisonAttributes() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/if-missing-structured-attribute.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_MISSING_ATTRIBUTE")));
  }

  /** Contract: non-numeric integer attributes must produce invalid-attribute diagnostics. */
  @Test
  void parserRejectsInvalidFieldLength() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: missing required attributes are reported explicitly. */
  @Test
  void parserRejectsMissingRequiredFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-missing-comment.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_MISSING_ATTRIBUTE")));
  }

  /** Contract: unsupported endian literals are rejected. */
  @Test
  void parserRejectsInvalidEndianValue() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-endian.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ENDIAN")));
  }

  /** Contract: a non-`schema` root element is invalid. */
  @Test
  void parserRejectsNonSchemaRootElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/invalid-root.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ROOT")));
  }

  /** Contract: zero is not accepted for positive integer attributes such as length. */
  @Test
  void parserRejectsZeroLengthFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-zero-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: missing XML files report a parser IO diagnostic instead of crashing. */
  @Test
  void parserReportsIoErrorForMissingSpecPath() {
    SpecParser parser = new SpecParser();
    Path missingPath = Path.of("build", "tmp", "does-not-exist-parser.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(missingPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_IO_ERROR")));
  }

  /** Contract: an empty XML document is rejected with `PARSER_EMPTY_DOCUMENT`. */
  @Test
  void parserRejectsEmptyDocument() throws Exception {
    SpecParser parser = new SpecParser();
    BmsException exception =
        parseInlineXmlExpectingFailure(parser, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic ->
                    diagnostic.code().equals("PARSER_EMPTY_DOCUMENT")
                        || diagnostic.code().equals("PARSER_XML_STREAM_ERROR")));
  }

  /** Contract: unsupported bitField size literals are rejected as invalid attributes. */
  @Test
  void parserRejectsInvalidBitFieldSizeValue() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <bitField name="status" size="u128" comment="status"/>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: unsupported child elements inside bitField fail fast. */
  @Test
  void parserRejectsUnsupportedElementInsideBitField() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <bitField name="status" size="u8" comment="status">
              <unexpected/>
            </bitField>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: unsupported child elements inside segment fail fast. */
  @Test
  void parserRejectsUnsupportedElementInsideSegment() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <bitField name="status" size="u8" comment="status">
              <segment name="mode" from="0" to="1" comment="mode">
                <unexpected/>
              </segment>
            </bitField>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: invalid float size literals are rejected as invalid attributes. */
  @Test
  void parserRejectsInvalidFloatSizeValue() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <float name="value" size="f128" encoding="ieee754" comment="value"/>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: invalid float encoding literals are rejected as invalid attributes. */
  @Test
  void parserRejectsInvalidFloatEncodingValue() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <float name="value" size="f32" encoding="custom" comment="value"/>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: non-decimal float scale values are rejected. */
  @Test
  void parserRejectsInvalidFloatScaleDecimal() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <float name="value" size="f32" encoding="scaled" scale="abc" comment="value"/>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: vectors with two length-mode children are rejected. */
  @Test
  void parserRejectsVectorWithMultipleLengthModeChildren() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values">
              <countField ref="count"/>
              <terminatorValue value="00"/>
            </vector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_FIELD_CONTENT")));
  }

  /** Contract: blobVector does not allow terminatorField length mode in this parser slice. */
  @Test
  void parserRejectsBlobVectorWithTerminatorFieldChild() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <blobVector name="payload" comment="payload">
              <terminatorField name="node">
                <terminatorMatch value="00"/>
              </terminatorField>
            </blobVector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: vectors must include exactly one recognized length-mode child. */
  @Test
  void parserRejectsVectorMissingLengthModeChild() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values"/>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_MISSING_CHILD_ELEMENT")));
  }

  /** Contract: unsupported vector length-mode children fail fast. */
  @Test
  void parserRejectsUnsupportedVectorLengthModeChild() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values">
              <unsupported/>
            </vector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: terminatorField supports at most one nested terminator node. */
  @Test
  void parserRejectsTerminatorFieldWithTwoChildren() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values">
              <terminatorField name="node">
                <terminatorMatch value="00"/>
                <terminatorMatch value="01"/>
              </terminatorField>
            </vector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_FIELD_CONTENT")));
  }

  /** Contract: unsupported terminatorField child elements fail fast. */
  @Test
  void parserRejectsUnsupportedTerminatorFieldChild() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values">
              <terminatorField name="node">
                <unsupported/>
              </terminatorField>
            </vector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: countField does not allow nested child elements. */
  @Test
  void parserRejectsNestedElementInsideCountField() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <vector name="values" elementType="uint8" comment="values">
              <countField ref="count">
                <unexpected/>
              </countField>
            </vector>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_FIELD_CONTENT")));
  }

  /** Contract: out-of-range unsigned byte attributes are rejected. */
  @Test
  void parserRejectsOutOfRangeFlagPosition() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <bitField name="status" size="u8" comment="status">
              <flag name="tooHigh" position="256" comment="bad"/>
            </bitField>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: negative variant values are rejected for unsigned-long attributes. */
  @Test
  void parserRejectsNegativeVariantValue() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema xmlns="http://example.com/binarymessage" namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <bitField name="status" size="u8" comment="status">
              <segment name="mode" from="0" to="1" comment="mode">
                <variant name="bad" value="-1" comment="bad"/>
              </segment>
            </bitField>
          </messageType>
        </schema>
        """;

    BmsException exception = parseInlineXmlExpectingFailure(parser, xml);
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  /** Contract: QName-style `field@type` values are normalized to local names. */
  @Test
  void parserNormalizesQNameFieldTypeValues() throws Exception {
    SpecParser parser = new SpecParser();
    String xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <schema
            xmlns="http://example.com/binarymessage"
            xmlns:bm="http://example.com/binarymessage"
            namespace="acme.telemetry">
          <messageType name="Frame" comment="frame">
            <field name="version" type="bm:uint8" comment="version"/>
          </messageType>
        </schema>
        """;

    Path specPath = writeTempSpec(xml);
    try {
      ParsedSchema parsedSchema = parser.parse(specPath);
      assertEquals("uint8", parsedSchema.messageTypes().get(0).fields().get(0).typeName());
    } finally {
      Files.deleteIfExists(specPath);
    }
  }

  /** Contract: malformed XML reports a stream/parsing error diagnostic. */
  @Test
  void parserReportsMalformedXml() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/malformed.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_XML_STREAM_ERROR")));
  }

  /**
   * Parses an in-memory XML string by writing it to a temporary file and expecting parser failure.
   *
   * @param parser parser under test
   * @param xml XML string to write and parse
   * @return raised parser exception
   * @throws Exception when temp-file IO fails
   */
  private static BmsException parseInlineXmlExpectingFailure(SpecParser parser, String xml)
      throws Exception {
    Path specPath = writeTempSpec(xml);
    try {
      return assertThrows(BmsException.class, () -> parser.parse(specPath));
    } finally {
      Files.deleteIfExists(specPath);
    }
  }

  /**
   * Writes XML text to a temporary file and returns that path.
   *
   * @param xml XML string to write
   * @return path to a temporary XML file
   * @throws Exception when temp-file IO fails
   */
  private static Path writeTempSpec(String xml) throws Exception {
    Path tempFile = Files.createTempFile("bms-parser-", ".xml");
    Files.writeString(tempFile, xml, StandardCharsets.UTF_8);
    return tempFile;
  }
}
