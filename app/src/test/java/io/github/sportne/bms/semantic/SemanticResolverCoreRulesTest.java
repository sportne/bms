package io.github.sportne.bms.semantic;

import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertHasDiagnostic;
import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertResolutionFails;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Semantic resolver tests for namespace, type lookup, and duplicate-name foundations. */
class SemanticResolverCoreRulesTest {

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_NAMESPACE");
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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_UNKNOWN_TYPE");
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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_FIELD_NAME");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_MEMBER_NAME");
  }

  @Test
  void semanticResolverRejectsDuplicateNamesAcrossReusableTypeGroups() {
    ParsedBitField bitField =
        new ParsedBitField(
            "DupType",
            BitFieldSize.U8,
            null,
            "status bits",
            List.of(new ParsedBitFlag("ready", 0, "ready")),
            List.of());
    ParsedBitField bitFieldDuplicate =
        new ParsedBitField(
            "DupType",
            BitFieldSize.U8,
            null,
            "status bits duplicate",
            List.of(new ParsedBitFlag("alarm", 1, "alarm")),
            List.of());

    ParsedSchema parsedSchema =
        new ParsedSchema(
            " ",
            List.of(
                new ParsedMessageType("DupType", "first", "bad..namespace", List.of()),
                new ParsedMessageType("DupType", "second", null, List.of())),
            List.of(bitField, bitFieldDuplicate),
            List.of(
                new ParsedFloat(
                    "DupType", FloatSize.F32, FloatEncoding.IEEE754, null, null, "float one"),
                new ParsedFloat(
                    "DupType", FloatSize.F32, FloatEncoding.IEEE754, null, null, "float two")),
            List.of(
                new ParsedScaledInt("DupType", "int16", new BigDecimal("1"), null, "scaled one"),
                new ParsedScaledInt(
                    "DupType", "notAType", new BigDecimal("1"), null, "scaled two")),
            List.of(
                new ParsedArray("DupType", "uint8", 2, null, "array one"),
                new ParsedArray("DupType", "uint8", 2, null, "array two")),
            List.of(
                new ParsedVector(
                    "DupType", "uint8", null, "vector one", new ParsedCountFieldLength("count")),
                new ParsedVector(
                    "DupType", "uint8", null, "vector two", new ParsedCountFieldLength("count"))),
            List.of(
                new ParsedBlobArray("DupType", 2, "blob array one"),
                new ParsedBlobArray("DupType", 2, "blob array two")),
            List.of(
                new ParsedBlobVector(
                    "DupType", "blob vector one", new ParsedTerminatorValueLength("00")),
                new ParsedBlobVector(
                    "DupType", "blob vector two", new ParsedTerminatorValueLength("00"))));

    BmsException exception = assertResolutionFails(parsedSchema);

    assertHasDiagnostic(exception, "SEMANTIC_INVALID_NAMESPACE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_MESSAGE_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_FLOAT_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_SCALED_INT_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_ARRAY_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_VECTOR_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE");
  }

  @Test
  void semanticResolverRejectsDuplicateNamesAcrossAllMessageMemberKinds() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedField("dup", "uint8", null, null, null, "dup field"),
                        new ParsedFloat(
                            "dup", FloatSize.F32, FloatEncoding.IEEE754, null, null, "dup float"),
                        new ParsedScaledInt(
                            "dup", "notAType", new BigDecimal("1"), null, "dup scaled"),
                        new ParsedArray("dup", "MissingType", 2, null, "dup array"),
                        new ParsedVector(
                            "dup",
                            "MissingType",
                            null,
                            "dup vector",
                            new ParsedCountFieldLength("laterCount")),
                        new ParsedBlobArray("dup", 4, "dup blob array"),
                        new ParsedBlobVector(
                            "dup", "dup blob vector", new ParsedTerminatorField("bad-name", null)),
                        new ParsedField("laterCount", "uint8", null, null, null, "later")))));

    BmsException exception = assertResolutionFails(parsedSchema);

    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_MEMBER_NAME");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_SCALED_INT_BASE_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_UNKNOWN_TYPE");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_COUNT_FIELD_REF");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_LENGTH_MODE");
  }
}
