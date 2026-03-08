package io.github.sportne.bms.parse;

import static io.github.sportne.bms.parse.SpecParserTestSupport.assertHasDiagnostic;
import static io.github.sportne.bms.parse.SpecParserTestSupport.assertHasDiagnosticContaining;
import static io.github.sportne.bms.parse.SpecParserTestSupport.parseInlineXmlExpectingFailure;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Parser contract tests for unsupported or invalid input.
 *
 * <p>These tests lock down fail-fast diagnostics so parser behavior stays predictable while the
 * supported feature set expands.
 */
class SpecParserFailureDiagnosticsTest {

  @Test
  void parserFailsFastForUnsupportedElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-root-unknown.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

  @Test
  void parserFailsFastForUnsupportedMessageTypeElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-message-unknown.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnosticContaining(exception, "PARSER_UNSUPPORTED_ELEMENT", "<messageType>");
  }

  @Test
  void parserRejectsMixedIfConditionAttributeStyles() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/if-invalid-attribute-combination.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

  @Test
  void parserRejectsMissingStructuredIfComparisonAttributes() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/if-missing-structured-attribute.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_MISSING_ATTRIBUTE");
  }

  @Test
  void parserRejectsInvalidFieldLength() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

  @Test
  void parserRejectsMissingRequiredFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-missing-comment.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_MISSING_ATTRIBUTE");
  }

  @Test
  void parserRejectsInvalidEndianValue() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-endian.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_INVALID_ENDIAN");
  }

  @Test
  void parserRejectsNonSchemaRootElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/invalid-root.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_INVALID_ROOT");
  }

  @Test
  void parserRejectsZeroLengthFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-zero-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

  @Test
  void parserReportsIoErrorForMissingSpecPath() {
    SpecParser parser = new SpecParser();
    Path missingPath = Path.of("build", "tmp", "does-not-exist-parser.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(missingPath));
    assertHasDiagnostic(exception, "PARSER_IO_ERROR");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

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
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_FIELD_CONTENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_MISSING_CHILD_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_FIELD_CONTENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_UNSUPPORTED_ELEMENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_FIELD_CONTENT");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

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
    assertHasDiagnostic(exception, "PARSER_INVALID_ATTRIBUTE");
  }

  @Test
  void parserReportsMalformedXml() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/malformed.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));
    assertHasDiagnostic(exception, "PARSER_XML_STREAM_ERROR");
  }
}
