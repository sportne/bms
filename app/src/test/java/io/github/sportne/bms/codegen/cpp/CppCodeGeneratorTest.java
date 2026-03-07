package io.github.sportne.bms.codegen.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.BitFieldSize;
import io.github.sportne.bms.model.Endian;
import io.github.sportne.bms.model.FloatEncoding;
import io.github.sportne.bms.model.FloatSize;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBitField;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedFloat;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract tests for the C++ generator.
 *
 * <p>These tests document both:
 *
 * <ul>
 *   <li>what output must stay stable for supported inputs
 *   <li>how unsupported members must fail (with diagnostics, not silent skips)
 * </ul>
 */
class CppCodeGeneratorTest {

  @TempDir Path tempDir;

  /** Contract: supported foundation messages generate deterministic C++ files. */
  @Test
  void cppGeneratorProducesDeterministicNamespaceAwareOutput() throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = sampleSchema();

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path headerHeaderPath = tempDir.resolve("acme/telemetry/Header.hpp");
    Path headerSourcePath = tempDir.resolve("acme/telemetry/Header.cpp");
    Path packetHeaderPath = tempDir.resolve("acme/telemetry/packet/Packet.hpp");
    Path packetSourcePath = tempDir.resolve("acme/telemetry/packet/Packet.cpp");

    assertTrue(Files.exists(headerHeaderPath));
    assertTrue(Files.exists(headerSourcePath));
    assertTrue(Files.exists(packetHeaderPath));
    assertTrue(Files.exists(packetSourcePath));

    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/Header.hpp"),
        Files.readString(headerHeaderPath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/Header.cpp"),
        Files.readString(headerSourcePath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/packet/Packet.hpp"),
        Files.readString(packetHeaderPath, StandardCharsets.UTF_8));
    assertEquals(
        TestSupport.readResource("golden/cpp/acme/telemetry/packet/Packet.cpp"),
        Files.readString(packetSourcePath, StandardCharsets.UTF_8));
  }

  /** Contract: numeric member kinds not yet emitted must fail with explicit diagnostic codes. */
  @Test
  void cppGeneratorFailsWithClearDiagnosticsForUnsupportedNumericMembers() {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "Telemetry",
            "Telemetry message",
            "acme.telemetry",
            List.of(
                new ResolvedBitField(
                    "statusBits", BitFieldSize.U8, null, "Status bits", List.of(), List.of()),
                new ResolvedField(
                    "temperature", new FloatTypeRef("TelemetryFloat"), null, null, null, "temp"),
                new ResolvedField(
                    "offset",
                    new ScaledIntTypeRef("TemperatureScaled"),
                    null,
                    null,
                    null,
                    "offset")));

    ResolvedSchema schema =
        new ResolvedSchema(
            "acme.telemetry",
            List.of(messageType),
            List.of(),
            List.of(
                new ResolvedFloat(
                    "TelemetryFloat",
                    FloatSize.F32,
                    FloatEncoding.IEEE754,
                    null,
                    null,
                    "Reusable float")),
            List.of(
                new ResolvedScaledInt(
                    "TemperatureScaled",
                    PrimitiveType.INT16,
                    new BigDecimal("0.1"),
                    null,
                    "Reusable scaled int")));

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_MEMBER")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_TYPE_REF")));
  }

  /** Contract: collection member kinds and collection type refs fail with explicit diagnostics. */
  @Test
  void cppGeneratorFailsWithClearDiagnosticsForUnsupportedCollectionMembers() {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "Collections",
            "Collection message",
            "acme.telemetry",
            List.of(
                new ResolvedArray(
                    "samples", new PrimitiveTypeRef(PrimitiveType.UINT8), 4, null, "Inline array"),
                new ResolvedField(
                    "reusableArray",
                    new ArrayTypeRef("BytePair"),
                    null,
                    null,
                    null,
                    "Field that references reusable array")));

    ResolvedSchema schema =
        new ResolvedSchema(
            "acme.telemetry",
            List.of(messageType),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ResolvedArray(
                    "BytePair",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    2,
                    null,
                    "Reusable array")),
            List.of(),
            List.of(),
            List.of());

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_MEMBER")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_TYPE_REF")));
  }

  /** Builds a small resolved schema fixture shared by generator tests. */
  private static ResolvedSchema sampleSchema() {
    ResolvedMessageType header =
        new ResolvedMessageType(
            "Header",
            "header",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "version",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    null,
                    null,
                    "version"),
                new ResolvedField(
                    "sequence",
                    new PrimitiveTypeRef(PrimitiveType.UINT16),
                    null,
                    Endian.LITTLE,
                    null,
                    "sequence")));

    ResolvedMessageType packet =
        new ResolvedMessageType(
            "Packet",
            "packet",
            "acme.telemetry.packet",
            List.of(
                new ResolvedField(
                    "header", new MessageTypeRef("Header"), null, null, null, "header"),
                new ResolvedField(
                    "payloadLength",
                    new PrimitiveTypeRef(PrimitiveType.UINT32),
                    null,
                    null,
                    null,
                    "payloadLength")));

    return new ResolvedSchema("acme.telemetry", List.of(header, packet));
  }
}
