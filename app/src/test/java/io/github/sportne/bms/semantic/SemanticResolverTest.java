package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for semantic resolution.
 *
 * <p>These tests define cross-field and cross-type rules that go beyond XSD validation.
 */
class SemanticResolverTest {

  /** Contract: effective namespace comes from schema default or per-message override. */
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

  /** Contract: invalid namespace syntax is rejected during semantic validation. */
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

  /** Contract: unknown field types are rejected with `SEMANTIC_UNKNOWN_TYPE`. */
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

  /** Contract: duplicate field/member names in one message are rejected. */
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

  /** Contract: bitField names must be valid identifiers at the message-member level. */
  @Test
  void semanticResolverRejectsInvalidBitFieldName() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedBitField(
                            "status-bits",
                            BitFieldSize.U8,
                            null,
                            "Status bits",
                            List.of(new ParsedBitFlag("ready", 0, "Ready flag")),
                            List.of())))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_BIT_FIELD_NAME")));
  }

  /** Contract: top-level message-member names are unique across fields and bitFields. */
  @Test
  void semanticResolverRejectsDuplicateTopLevelNameBetweenFieldAndBitField() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedField("status", "uint8", null, null, null, "status"),
                        new ParsedBitField(
                            "status",
                            BitFieldSize.U8,
                            null,
                            "Status bits",
                            List.of(new ParsedBitFlag("ready", 0, "Ready flag")),
                            List.of())))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_MEMBER_NAME")));
  }

  /** Contract: schema-level reusable bitField names must be unique at that level. */
  @Test
  void semanticResolverRejectsDuplicateRootBitFieldNames() {
    ParsedBitField first =
        new ParsedBitField(
            "statusWord",
            BitFieldSize.U8,
            null,
            "Status bits",
            List.of(new ParsedBitFlag("ready", 0, "Ready flag")),
            List.of());
    ParsedBitField second =
        new ParsedBitField(
            "statusWord",
            BitFieldSize.U8,
            null,
            "Other status bits",
            List.of(new ParsedBitFlag("alarm", 1, "Alarm flag")),
            List.of());

    ParsedSchema parsedSchema =
        new ParsedSchema("acme.telemetry", List.of(), List.of(first, second), List.of(), List.of());

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_ROOT_BIT_FIELD_NAME")));
  }

  /** Contract: nested flag/segment names can repeat across different top-level bitFields. */
  @Test
  void semanticResolverAllowsNestedNameReuseAcrossDifferentBitFields() throws Exception {
    ParsedBitField first =
        new ParsedBitField(
            "statusA",
            BitFieldSize.U8,
            null,
            "First status bits",
            List.of(new ParsedBitFlag("ready", 0, "Ready flag")),
            List.of());
    ParsedBitField second =
        new ParsedBitField(
            "statusB",
            BitFieldSize.U8,
            null,
            "Second status bits",
            List.of(new ParsedBitFlag("ready", 0, "Ready flag")),
            List.of());

    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(new ParsedMessageType("Header", "header", null, List.of(first, second))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals(2, resolved.messageTypes().get(0).members().size());
  }

  /** Contract: reusable float/scaledInt type names resolve to dedicated type refs. */
  @Test
  void semanticResolverResolvesReusableNumericTypeReferences() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedField("temperature", "TelemetryFloat", null, null, null, "temp"),
                        new ParsedField(
                            "offset", "TemperatureScaled", null, null, null, "offset")))),
            List.of(),
            List.of(
                new ParsedFloat(
                    "TelemetryFloat",
                    FloatSize.F32,
                    FloatEncoding.IEEE754,
                    null,
                    null,
                    "Reusable float")),
            List.of(
                new ParsedScaledInt(
                    "TemperatureScaled", "int16", new BigDecimal("0.01"), null, "Scaled int")));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals(1, resolved.reusableFloats().size());
    assertEquals(1, resolved.reusableScaledInts().size());
    assertInstanceOf(FloatTypeRef.class, resolved.messageTypes().get(0).fields().get(0).typeRef());
    assertInstanceOf(
        ScaledIntTypeRef.class, resolved.messageTypes().get(0).fields().get(1).typeRef());
  }

  /** Contract: bit flags cannot share the same bit position. */
  @Test
  void semanticResolverRejectsDuplicateFlagPosition() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedBitField(
                            "statusBits",
                            BitFieldSize.U8,
                            null,
                            "Status bits",
                            List.of(
                                new ParsedBitFlag("ready", 0, "Ready flag"),
                                new ParsedBitFlag("alarm", 0, "Alarm flag")),
                            List.of())))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_FLAG_POSITION")));
  }

  /** Contract: names inside one bitField must be unique across flags and segments. */
  @Test
  void semanticResolverRejectsDuplicateBitFieldMemberName() {
    ParsedBitSegment segment =
        new ParsedBitSegment(
            "status",
            1,
            2,
            "status bits",
            List.of(new ParsedBitVariant("off", BigInteger.ZERO, "off status")));
    ParsedBitField bitField =
        new ParsedBitField(
            "statusBits",
            BitFieldSize.U8,
            null,
            "Status bits",
            List.of(new ParsedBitFlag("status", 0, "status flag")),
            List.of(segment));

    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(new ParsedMessageType("Header", "header", null, List.of(bitField))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_BIT_MEMBER_NAME")));
  }

  /** Contract: segment variant values must fit within the segment bit width. */
  @Test
  void semanticResolverRejectsVariantOutsideSegmentBitRange() {
    ParsedBitSegment segment =
        new ParsedBitSegment(
            "mode",
            1,
            2,
            "mode bits",
            List.of(new ParsedBitVariant("tooLarge", new BigInteger("7"), "out of range")));
    ParsedBitField bitField =
        new ParsedBitField(
            "statusBits", BitFieldSize.U8, null, "Status bits", List.of(), List.of(segment));

    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(new ParsedMessageType("Header", "header", null, List.of(bitField))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_VARIANT_VALUE")));
  }

  /** Contract: ieee754 float encoding must not define a scale value. */
  @Test
  void semanticResolverRejectsInvalidFloatScaleCombination() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Header",
                    "header",
                    null,
                    List.of(
                        new ParsedFloat(
                            "temperature",
                            FloatSize.F32,
                            FloatEncoding.IEEE754,
                            new BigDecimal("0.1"),
                            null,
                            "Invalid float scale")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_FLOAT_SCALE")));
  }
}
