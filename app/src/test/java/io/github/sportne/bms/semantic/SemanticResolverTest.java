package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticResolverTest {

  @Test
  void semanticResolverAppliesSchemaNamespaceAndMessageOverride() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(new ParsedField("version", "uint8", null, null, null, "version"))),
                new ParsedMessageType(
                    "Packet",
                    "packet",
                    "acme.telemetry.packet",
                    List.of(new ParsedField("header", "Header", null, null, null, "header")))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals("acme.telemetry", resolved.messageTypes().get(0).effectiveNamespace());
    assertEquals("acme.telemetry.packet", resolved.messageTypes().get(1).effectiveNamespace());
  }

  @Test
  void semanticResolverRejectsInvalidNamespace() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme..telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(new ParsedField("version", "uint8", null, null, null, "version")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_NAMESPACE")));
  }

  @Test
  void semanticResolverRejectsUnknownTypeReferences() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedField("version", "MissingType", null, null, null, "version")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_UNKNOWN_TYPE")));
  }

  @Test
  void semanticResolverRejectsDuplicateFieldNames() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedField("version", "uint8", null, null, null, "version"),
                        new ParsedField("version", "uint16", null, null, null, "version")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_FIELD_NAME")));
  }
}
