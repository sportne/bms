package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.parsed.ParsedArray;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedBlobArray;
import io.github.sportne.bms.model.parsed.ParsedBlobVector;
import io.github.sportne.bms.model.parsed.ParsedCountFieldLength;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedLengthMode;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.parsed.ParsedTerminatorField;
import io.github.sportne.bms.model.parsed.ParsedTerminatorValueLength;
import io.github.sportne.bms.model.parsed.ParsedVector;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
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

  /** Contract: collection definitions and collection type refs resolve in the front-end layer. */
  @Test
  void semanticResolverResolvesCollectionTypeReferences() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedField("count", "uint16", null, null, null, "count"),
                        new ParsedArray("samples", "uint8", 4, null, "inline array"),
                        new ParsedVector(
                            "events",
                            "uint8",
                            null,
                            "inline counted vector",
                            new ParsedCountFieldLength("count")),
                        new ParsedBlobArray("hash", 8, "inline blob array"),
                        new ParsedBlobVector(
                            "payload", "inline blob vector", new ParsedTerminatorValueLength("00")),
                        new ParsedField("reusableArray", "ReusableArray", null, null, null, "a"),
                        new ParsedField("reusableVector", "ReusableVector", null, null, null, "v"),
                        new ParsedField(
                            "reusableBlobArray", "ReusableBlobArray", null, null, null, "ba"),
                        new ParsedField(
                            "reusableBlobVector", "ReusableBlobVector", null, null, null, "bv")))),
            List.of(),
            List.of(),
            List.of(),
            List.of(new ParsedArray("ReusableArray", "uint8", 2, null, "Reusable array")),
            List.of(
                new ParsedVector(
                    "ReusableVector",
                    "uint8",
                    null,
                    "Reusable vector",
                    new ParsedCountFieldLength("futureCount"))),
            List.of(new ParsedBlobArray("ReusableBlobArray", 16, "Reusable blob array")),
            List.of(
                new ParsedBlobVector(
                    "ReusableBlobVector",
                    "Reusable blob vector",
                    new ParsedTerminatorValueLength("FF"))));

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");
    var frame = resolved.messageTypes().get(0);

    assertEquals(1, resolved.reusableArrays().size());
    assertEquals(1, resolved.reusableVectors().size());
    assertEquals(1, resolved.reusableBlobArrays().size());
    assertEquals(1, resolved.reusableBlobVectors().size());

    var fieldByName =
        frame.fields().stream()
            .collect(java.util.stream.Collectors.toMap(field -> field.name(), field -> field));
    assertInstanceOf(ArrayTypeRef.class, fieldByName.get("reusableArray").typeRef());
    assertInstanceOf(VectorTypeRef.class, fieldByName.get("reusableVector").typeRef());
    assertInstanceOf(BlobArrayTypeRef.class, fieldByName.get("reusableBlobArray").typeRef());
    assertInstanceOf(BlobVectorTypeRef.class, fieldByName.get("reusableBlobVector").typeRef());
  }

  /** Contract: message-level countField refs must target earlier primitive scalar fields. */
  @Test
  void semanticResolverRejectsVectorCountFieldRefToLaterField() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedVector(
                            "events",
                            "uint8",
                            null,
                            "inline counted vector",
                            new ParsedCountFieldLength("count")),
                        new ParsedField("count", "uint16", null, null, null, "count")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_COUNT_FIELD_REF")));
  }

  /** Contract: reusable vector countField refs are syntax-only checks in this milestone. */
  @Test
  void semanticResolverAllowsReusableVectorCountFieldSyntaxOnlyRef() throws Exception {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ParsedVector(
                    "ReusableVector",
                    "uint8",
                    null,
                    "Reusable vector",
                    new ParsedCountFieldLength("futureCount"))),
            List.of(),
            List.of());

    var resolved = new SemanticResolver().resolve(parsedSchema, "test.xml");

    assertEquals(1, resolved.reusableVectors().size());
    ParsedLengthMode parsedMode = parsedSchema.reusableVectors().get(0).lengthMode();
    assertInstanceOf(ParsedCountFieldLength.class, parsedMode);
    assertInstanceOf(
        ResolvedCountFieldLength.class, resolved.reusableVectors().get(0).lengthMode());
  }

  /** Contract: terminatorField paths must end with a terminatorMatch leaf. */
  @Test
  void semanticResolverRejectsTerminatorFieldPathWithoutMatchLeaf() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedVector(
                            "pathData",
                            "uint8",
                            null,
                            "path vector",
                            new ParsedTerminatorField(
                                "outer", new ParsedTerminatorField("inner", null)))))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_TERMINATOR_FIELD_PATH")));
  }

  /** Contract: top-level names are unique even when arrays share a field's name. */
  @Test
  void semanticResolverRejectsDuplicateTopLevelNameBetweenFieldAndArray() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(
                        new ParsedField("payload", "uint8", null, null, null, "payload field"),
                        new ParsedArray("payload", "uint8", 4, null, "payload array")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_MEMBER_NAME")));
  }

  /** Contract: unknown collection element types fail with the standard unknown-type diagnostic. */
  @Test
  void semanticResolverRejectsUnknownCollectionElementType() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "CollectionFrame",
                    "collection frame",
                    null,
                    List.of(new ParsedArray("samples", "MissingElement", 4, null, "samples")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_UNKNOWN_TYPE")));
  }

  /** Contract: duplicate reusable names are rejected across all reusable collection/type groups. */
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

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_NAMESPACE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_MESSAGE_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_FLOAT_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_SCALED_INT_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_ARRAY_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_VECTOR_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_BLOB_ARRAY_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_BLOB_VECTOR_TYPE")));
  }

  /** Contract: duplicate message member names are enforced across all member kinds. */
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

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_MEMBER_NAME")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_SCALED_INT_BASE_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_UNKNOWN_TYPE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_COUNT_FIELD_REF")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_LENGTH_MODE")));
  }

  /** Contract: bitField semantic checks catch duplicate names, positions, ranges, and variants. */
  @Test
  void semanticResolverRejectsDeepBitFieldConsistencyViolations() {
    ParsedBitSegment firstSegment =
        new ParsedBitSegment(
            "segment",
            3,
            1,
            "bad range",
            List.of(
                new ParsedBitVariant("mode", BigInteger.ZERO, "mode"),
                new ParsedBitVariant("mode", BigInteger.ONE, "duplicate name")));
    ParsedBitSegment secondSegment =
        new ParsedBitSegment(
            "segment",
            7,
            8,
            "out of range",
            List.of(new ParsedBitVariant("tooLarge", new BigInteger("9"), "too large")));
    ParsedBitField bitField =
        new ParsedBitField(
            "statusWord",
            BitFieldSize.U8,
            null,
            "status bits",
            List.of(
                new ParsedBitFlag("dup", 0, "dup"),
                new ParsedBitFlag("dup", 8, "dup and out of range"),
                new ParsedBitFlag("other", 0, "duplicate position")),
            List.of(firstSegment, secondSegment));

    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(new ParsedMessageType("Frame", "frame", null, List.of(bitField))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_FLAG_NAME")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_BIT_POSITION")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_FLAG_POSITION")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_SEGMENT_NAME")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_SEGMENT_RANGE")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_DUPLICATE_VARIANT_NAME")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_VARIANT_VALUE")));
  }

  /** Contract: scaled floats without a scale value are rejected in semantic validation. */
  @Test
  void semanticResolverRejectsScaledFloatWithoutScale() {
    ParsedSchema parsedSchema =
        new ParsedSchema(
            "acme.telemetry",
            List.of(
                new ParsedMessageType(
                    "Frame",
                    "frame",
                    null,
                    List.of(
                        new ParsedFloat(
                            "temperature",
                            FloatSize.F32,
                            FloatEncoding.SCALED,
                            null,
                            null,
                            "temperature")))));

    BmsException exception =
        assertThrows(
            BmsException.class, () -> new SemanticResolver().resolve(parsedSchema, "test.xml"));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("SEMANTIC_INVALID_FLOAT_SCALE")));
  }
}
