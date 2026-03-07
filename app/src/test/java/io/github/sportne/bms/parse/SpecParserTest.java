package io.github.sportne.bms.parse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SpecParserTest {

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

  @Test
  void parserFailsFastForUnsupportedElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/unsupported-root-bitfield.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_UNSUPPORTED_ELEMENT")));
  }

  @Test
  void parserRejectsInvalidFieldLength() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

  @Test
  void parserRejectsMissingRequiredFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-missing-comment.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_MISSING_ATTRIBUTE")));
  }

  @Test
  void parserRejectsInvalidEndianValue() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-invalid-endian.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ENDIAN")));
  }

  @Test
  void parserRejectsNonSchemaRootElement() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/invalid-root.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ROOT")));
  }

  @Test
  void parserRejectsZeroLengthFieldAttribute() {
    SpecParser parser = new SpecParser();
    Path specPath = TestSupport.resourcePath("specs/field-zero-length.xml");

    BmsException exception = assertThrows(BmsException.class, () -> parser.parse(specPath));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("PARSER_INVALID_ATTRIBUTE")));
  }

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
