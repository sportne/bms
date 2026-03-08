package io.github.sportne.bms.codegen.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.model.resolved.ArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobArrayTypeRef;
import io.github.sportne.bms.model.resolved.BlobVectorTypeRef;
import io.github.sportne.bms.model.resolved.FloatTypeRef;
import io.github.sportne.bms.model.resolved.MessageTypeRef;
import io.github.sportne.bms.model.resolved.PrimitiveType;
import io.github.sportne.bms.model.resolved.PrimitiveTypeRef;
import io.github.sportne.bms.model.resolved.ResolvedArray;
import io.github.sportne.bms.model.resolved.ResolvedBlobVector;
import io.github.sportne.bms.model.resolved.ResolvedCountFieldLength;
import io.github.sportne.bms.model.resolved.ResolvedField;
import io.github.sportne.bms.model.resolved.ResolvedMessageType;
import io.github.sportne.bms.model.resolved.ResolvedScaledInt;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorField;
import io.github.sportne.bms.model.resolved.ResolvedTerminatorValueLength;
import io.github.sportne.bms.model.resolved.ResolvedVector;
import io.github.sportne.bms.model.resolved.ScaledIntTypeRef;
import io.github.sportne.bms.model.resolved.VarStringTypeRef;
import io.github.sportne.bms.model.resolved.VectorTypeRef;
import io.github.sportne.bms.testutil.TestSupport;
import io.github.sportne.bms.util.BmsException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract tests for the Java generator.
 *
 * <p>These tests define what must remain stable for generated Java output and how unsupported
 * deferred member kinds must be reported.
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

  /** Contract: numeric-slice specs generate deterministic Java output with bitfield helpers. */
  @Test
  void javaGeneratorProducesDeterministicNumericSliceOutput() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/numeric-slice-valid.xml");

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path outputPath = tempDir.resolve("acme/telemetry/numeric/TelemetryFrame.java");
    String expected =
        TestSupport.readResource("golden/java/acme/telemetry/numeric/TelemetryFrame.java");
    String actual = Files.readString(outputPath, StandardCharsets.UTF_8);

    assertEquals(expected, actual);
    assertTrue(actual.contains("public boolean isStatusBitsAlarm()"));
    assertTrue(actual.contains("Math.round(rawValue)"));
    assertTrue(actual.contains("scaleToSignedRaw(this.temperature, 0.1d"));
  }

  /** Contract: collection-slice specs generate deterministic Java output for reusable refs. */
  @Test
  void javaGeneratorProducesDeterministicCollectionSliceOutput() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/collections-slice-valid.xml");

    generator.generate(schema, tempDir);
    generator.generate(schema, tempDir);

    Path outputPath = tempDir.resolve("acme/telemetry/collections/CollectionFrame.java");
    String expected =
        TestSupport.readResource("golden/java/acme/telemetry/collections/CollectionFrame.java");
    String actual = Files.readString(outputPath, StandardCharsets.UTF_8);

    assertEquals(expected, actual);
    assertTrue(
        actual.contains("expectedEventsCount = requireCount((this.count & 0xFFFFL), \"count\")"));
    assertTrue(actual.contains("public short[] reusableVectorField;"));
    assertTrue(actual.contains("ArrayList<Short> itemPathDataList = new ArrayList<>();"));
  }

  /** Contract: the coverage fixture drives extended float/scaled/collection Java branches. */
  @Test
  void javaGeneratorCoversExtendedNumericAndCollectionBranches() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/java-generator-coverage-valid.xml");

    generator.generate(schema, tempDir);

    Path outputPath = tempDir.resolve("acme/telemetry/coverage/ExtendedFrame.java");
    String source = Files.readString(outputPath, StandardCharsets.UTF_8);

    assertTrue(source.contains("import acme.telemetry.coverage.shared.Child;"));
    assertTrue(
        source.contains(
            "writeUInt16(out, Short.toUnsignedInt(Float.floatToFloat16((float) this.halfIeeeInline))"));
    assertTrue(
        source.contains(
            "writeInt64(out, Double.doubleToLongBits(this.doubleIeeeInline), ByteOrder.BIG_ENDIAN);"));
    assertTrue(source.contains("writeInt16(out, (short) scaleToSignedRaw(this.halfScaledInline"));
    assertTrue(source.contains("writeInt64(out, scaleToSignedRaw(this.doubleScaledInline"));
    assertTrue(source.contains("writeInt8(out, (byte) scaleToSignedRaw(this.i8ScaledInline"));
    assertTrue(source.contains("writeUInt8(out, (short) scaleToUnsignedRaw(this.u8ScaledInline"));
    assertTrue(source.contains("writeUInt32(out, scaleToUnsignedRaw(this.u32ScaledInline"));
    assertTrue(source.contains("requireCountUnsignedLong(this.count64, \"count64\")"));
    assertTrue(source.contains("ArrayList<Integer> itemU16TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("ArrayList<Long> itemU32TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("ArrayList<Long> itemU64TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("Long.compareUnsigned(itemU64Terminated"));
    assertTrue(source.contains("ArrayList<Byte> itemI8TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("ArrayList<Short> itemI16TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("ArrayList<Integer> itemI32TerminatedList = new ArrayList<>();"));
    assertTrue(source.contains("ArrayList<Long> itemI64TerminatedList = new ArrayList<>();"));
  }

  /** Contract: missing reusable type references report explicit, user-readable diagnostics. */
  @Test
  void javaGeneratorReportsMissingReusableTypeReferenceDiagnostics() {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "MissingRefs",
            "message with unresolved reusable refs",
            "acme.telemetry",
            List.of(
                new ResolvedField(
                    "floatField", new FloatTypeRef("MissingFloat"), null, null, null, "float"),
                new ResolvedField(
                    "scaledField",
                    new ScaledIntTypeRef("MissingScaled"),
                    null,
                    null,
                    null,
                    "scaled"),
                new ResolvedField(
                    "arrayField", new ArrayTypeRef("MissingArray"), null, null, null, "array"),
                new ResolvedField(
                    "vectorField", new VectorTypeRef("MissingVector"), null, null, null, "vector"),
                new ResolvedField(
                    "blobArrayField",
                    new BlobArrayTypeRef("MissingBlobArray"),
                    null,
                    null,
                    null,
                    "blob array"),
                new ResolvedField(
                    "blobVectorField",
                    new BlobVectorTypeRef("MissingBlobVector"),
                    null,
                    null,
                    null,
                    "blob vector")));

    ResolvedSchema schema = new ResolvedSchema("acme.telemetry", List.of(messageType));

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir));

    String diagnosticsText =
        exception.diagnostics().stream()
            .map(diagnostic -> diagnostic.message())
            .collect(Collectors.joining("\n"));
    assertTrue(diagnosticsText.contains("Reusable float was not found: MissingFloat"));
    assertTrue(diagnosticsText.contains("Reusable scaledInt was not found: MissingScaled"));
    assertTrue(diagnosticsText.contains("Reusable array was not found: MissingArray"));
    assertTrue(diagnosticsText.contains("Reusable vector was not found: MissingVector"));
    assertTrue(diagnosticsText.contains("Reusable blobArray was not found: MissingBlobArray"));
    assertTrue(diagnosticsText.contains("Reusable blobVector was not found: MissingBlobVector"));
  }

  /**
   * Contract: unsupported collection and count/terminator semantics fail with clear diagnostics.
   */
  @Test
  void javaGeneratorReportsUnsupportedCollectionDiagnostics() {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "UnsupportedVectors",
            "message with unsupported collection combinations",
            "acme.telemetry",
            List.of(
                new ResolvedArray(
                    "badArray", new VarStringTypeRef("DeferredLabel"), 2, null, "bad array"),
                new ResolvedVector(
                    "badVectorElement",
                    new VarStringTypeRef("DeferredLabel"),
                    null,
                    "bad vector element",
                    new ResolvedCountFieldLength("count")),
                new ResolvedVector(
                    "badCountRef",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    "bad count ref",
                    new ResolvedCountFieldLength("missingCount")),
                new ResolvedVector(
                    "badTerminatorPath",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    "bad path",
                    new ResolvedTerminatorField("root", null)),
                new ResolvedVector(
                    "badTerminatorType",
                    new MessageTypeRef("Child"),
                    null,
                    "bad terminator element",
                    new ResolvedTerminatorValueLength("1")),
                new ResolvedVector(
                    "badTerminatorRange",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    "bad range",
                    new ResolvedTerminatorValueLength("300")),
                new ResolvedVector(
                    "badTerminatorLiteral",
                    new PrimitiveTypeRef(PrimitiveType.UINT8),
                    null,
                    "bad literal",
                    new ResolvedTerminatorValueLength("GG")),
                new ResolvedBlobVector(
                    "badBlobCountRef",
                    "bad blob count ref",
                    new ResolvedCountFieldLength("blobCount")),
                new ResolvedScaledInt(
                    "badScaled64", PrimitiveType.UINT64, BigDecimal.ONE, null, "unsupported")));

    ResolvedSchema schema = new ResolvedSchema("acme.telemetry", List.of(messageType));

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir));

    String diagnosticsText =
        exception.diagnostics().stream()
            .map(diagnostic -> diagnostic.message())
            .collect(Collectors.joining("\n"));
    assertTrue(diagnosticsText.contains("ResolvedArray(elementType=VarStringTypeRef)"));
    assertTrue(diagnosticsText.contains("badCountRef(countField ref=\"missingCount\")"));
    assertTrue(
        diagnosticsText.contains(
            "badTerminatorPath(terminatorField path missing terminatorMatch)"));
    assertTrue(
        diagnosticsText.contains(
            "badTerminatorType(terminator modes require primitive element types)"));
    assertTrue(
        diagnosticsText.contains("badTerminatorRange(terminator literal out of range: 300)"));
    assertTrue(diagnosticsText.contains("badTerminatorLiteral(invalid terminator literal: GG)"));
    assertTrue(diagnosticsText.contains("badBlobCountRef(countField ref=\"blobCount\")"));
    assertTrue(diagnosticsText.contains("ResolvedScaledInt(baseType=UINT64)"));
  }

  /** Contract: milestone-03 members still fail clearly because backend emission is deferred. */
  @Test
  void javaGeneratorFailsWithClearDiagnosticsForDeferredMilestoneThreeMembers() throws Exception {
    JavaCodeGenerator generator = new JavaCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/milestone-03-valid.xml");

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

  /**
   * Compiles one XML fixture all the way to the resolved model.
   *
   * @param resourcePath classpath resource path to the XML fixture
   * @return resolved schema produced by the front-end pipeline
   * @throws Exception if fixture compilation fails
   */
  private static ResolvedSchema compileFixture(String resourcePath) throws Exception {
    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    return compiler.compile(TestSupport.resourcePath(resourcePath));
  }
}
