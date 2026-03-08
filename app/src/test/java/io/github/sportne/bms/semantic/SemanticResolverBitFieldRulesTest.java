package io.github.sportne.bms.semantic;

import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertHasDiagnostic;
import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertResolutionFails;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.parsed.ParsedBitField;
import io.github.sportne.bms.model.parsed.ParsedBitFlag;
import io.github.sportne.bms.model.parsed.ParsedBitSegment;
import io.github.sportne.bms.model.parsed.ParsedBitVariant;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.util.BmsException;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Semantic resolver tests that lock down bit-field-specific validation behavior. */
class SemanticResolverBitFieldRulesTest {

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_BIT_FIELD_NAME");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_ROOT_BIT_FIELD_NAME");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_FLAG_POSITION");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_BIT_MEMBER_NAME");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_VARIANT_VALUE");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);

    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_FLAG_NAME");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_BIT_POSITION");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_FLAG_POSITION");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_SEGMENT_NAME");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_SEGMENT_RANGE");
    assertHasDiagnostic(exception, "SEMANTIC_DUPLICATE_VARIANT_NAME");
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_VARIANT_VALUE");
  }
}
