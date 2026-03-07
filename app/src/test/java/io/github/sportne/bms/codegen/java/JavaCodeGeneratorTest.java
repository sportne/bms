package io.github.sportne.bms.codegen.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.BitFieldSize;
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
 * Contract tests for the Java generator.
 *
 * <p>These tests define what must remain stable for generated Java output and how unsupported
 * member kinds must be reported.
 */
class JavaCodeGeneratorTest {

  @TempDir Path tempDir;

  /** Contract: supported foundation messages generate deterministic Java source files. */
  @Test
  void javaGeneratorProducesDeterministicNamespaceAwareOutput() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = sampleSchema();

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path headerPath = tempDir.resolve("acme/telemetry/Header.java");
    Path packetPath = tempDir.resolve("acme/telemetry/packet/Packet.java");

    assertTrue(Files.exists(headerPath));
    assertTrue(Files.exists(packetPath));

    String expectedHeader = TestSupport.readResource("golden/java/acme/telemetry/Header.java");
    String expectedPacket =
        TestSupport.readResource("golden/java/acme/telemetry/packet/Packet.java");

    assertEquals(expectedHeader, Files.readString(headerPath, StandardCharsets.UTF_8));
    assertEquals(expectedPacket, Files.readString(packetPath, StandardCharsets.UTF_8));
  }

  /** Contract: unsupported numeric members must fail with explicit diagnostic codes. */
  @Test
  void javaGeneratorFailsWithClearDiagnosticsForUnsupportedNumericMembers() {
    JavaCodeGenerator generator = new JavaCodeGenerator();
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
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_JAVA_UNSUPPORTED_MEMBER")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("GENERATOR_JAVA_UNSUPPORTED_TYPE_REF")));
  }

  /** Contract: collection member kinds and collection type refs fail with explicit diagnostics. */
  @Test
  void javaGeneratorFailsWithClearDiagnosticsForUnsupportedCollectionMembers() {
    JavaCodeGenerator generator = new JavaCodeGenerator();
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
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_JAVA_UNSUPPORTED_MEMBER")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("GENERATOR_JAVA_UNSUPPORTED_TYPE_REF")));
  }

  /** Contract: generator emits encode/decode code paths for every primitive integer type. */
  @Test
  void javaGeneratorCoversAllPrimitiveEncodeDecodeBranches() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedMessageType primitives =
        new ResolvedMessageType(
            "PrimitiveFrame",
            "primitive frame",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "u8", new PrimitiveTypeRef(PrimitiveType.UINT8), null, null, null, "u8"),
                new ResolvedField(
                    "u16",
                    new PrimitiveTypeRef(PrimitiveType.UINT16),
                    null,
                    io.github.sportne.bms.model.Endian.LITTLE,
                    null,
                    "u16"),
                new ResolvedField(
                    "u32", new PrimitiveTypeRef(PrimitiveType.UINT32), null, null, null, "u32"),
                new ResolvedField(
                    "u64", new PrimitiveTypeRef(PrimitiveType.UINT64), null, null, null, "u64"),
                new ResolvedField(
                    "i8", new PrimitiveTypeRef(PrimitiveType.INT8), null, null, null, "i8"),
                new ResolvedField(
                    "i16", new PrimitiveTypeRef(PrimitiveType.INT16), null, null, null, "i16"),
                new ResolvedField(
                    "i32", new PrimitiveTypeRef(PrimitiveType.INT32), null, null, null, "i32"),
                new ResolvedField(
                    "i64", new PrimitiveTypeRef(PrimitiveType.INT64), null, null, null, "i64"),
                new ResolvedField(
                    "child", new MessageTypeRef("MissingMessage"), null, null, null, "child")));

    ResolvedSchema schema = new ResolvedSchema("acme.telemetry", List.of(primitives));

    generator.generate(schema, tempDir);

    Path outputPath = tempDir.resolve("acme/telemetry/PrimitiveFrame.java");
    String source = Files.readString(outputPath, StandardCharsets.UTF_8);

    assertTrue(source.contains("writeUInt64"));
    assertTrue(source.contains("writeInt64"));
    assertTrue(source.contains("readUInt64"));
    assertTrue(source.contains("readInt64"));
    assertTrue(source.contains("public MissingMessage child;"));
  }

  /** Contract: IO failures when writing files return a generator IO diagnostic. */
  @Test
  void javaGeneratorReportsIoFailures() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = sampleSchema();

    Path blockedPath = tempDir.resolve("acme");
    Files.writeString(blockedPath, "not-a-directory", StandardCharsets.UTF_8);

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_JAVA_IO_ERROR")));
  }

  /** Builds a small resolved schema fixture shared by generator tests. */
  static ResolvedSchema sampleSchema() {
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
                    io.github.sportne.bms.model.Endian.LITTLE,
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
