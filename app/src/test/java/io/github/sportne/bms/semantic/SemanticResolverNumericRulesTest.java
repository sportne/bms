package io.github.sportne.bms.semantic;

import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertHasDiagnostic;
import static io.github.sportne.bms.semantic.SemanticResolverTestSupport.assertResolutionFails;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.parsed.ParsedField;
import io.github.sportne.bms.model.parsed.ParsedFloat;
import io.github.sportne.bms.model.parsed.ParsedMessageType;
import io.github.sportne.bms.model.parsed.ParsedScaledInt;
import io.github.sportne.bms.model.parsed.ParsedSchema;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Semantic resolver tests for reusable numeric type references and numeric validation rules. */
class SemanticResolverNumericRulesTest {

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_FLOAT_SCALE");
  }

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

    BmsException exception = assertResolutionFails(parsedSchema);
    assertHasDiagnostic(exception, "SEMANTIC_INVALID_FLOAT_SCALE");
  }
}
