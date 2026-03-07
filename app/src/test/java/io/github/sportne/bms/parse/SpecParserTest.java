package io.github.sportne.bms.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
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
    Path specPath = TestSupport.resourcePath("specs/unsupported-root-array.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  /** Contract: unsupported nested message members fail fast with clear context. */
  @Test
  void parserFailsFastForUnsupportedMessageTypeElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-message-vector.xml");

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
    assertEquals("TelemetryFloat", parsedSchema.reusableFloats().get(0).name());

    var message = parsedSchema.messageTypes().get(0);
    assertEquals("TelemetryFrame", message.name());
    assertEquals(6, message.members().size());
    assertEquals("version", message.fields().get(0).name());
    assertTrue(message.members().get(1) instanceof ParsedBitField);
    assertEquals("temperature", ((ParsedFloat) message.members().get(2)).name());
    assertEquals(FloatEncoding.SCALED, ((ParsedFloat) message.members().get(2)).encoding());
    assertTrue(message.members().get(3) instanceof ParsedScaledInt);
    assertEquals("reusableTemperature", message.fields().get(1).name());
    assertEquals("reusableFloat", message.fields().get(2).name());
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
}
