package io.github.sportne.bms.codegen.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.model.Endian;
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
import io.github.sportne.bms.model.resolved.ResolvedMessageMember;
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
import io.github.sportne.bms.util.Diagnostic;
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
 * <p>These tests verify deterministic output for supported fixtures and explicit failures for
 * deferred fixture features.
 */
class CppCodeGeneratorTest {

  @TempDir Path tempDir;

  /** Contract: foundation fixtures still generate deterministic C++ output. */
  @Test
  void cppGeneratorProducesDeterministicFoundationOutput() throws Exception {
    assertFixtureMatchesGolden(
        "specs/valid-foundation.xml",
        tempDir.resolve("foundation"),
        List.of(
            "acme/telemetry/Header.hpp",
            "acme/telemetry/Header.cpp",
            "acme/telemetry/packet/Packet.hpp",
            "acme/telemetry/packet/Packet.cpp"));
  }

  /** Contract: numeric slice fixtures generate deterministic C++ output. */
  @Test
  void cppGeneratorProducesDeterministicNumericSliceOutput() throws Exception {
    assertFixtureMatchesGolden(
        "specs/numeric-slice-valid.xml",
        tempDir.resolve("numeric"),
        List.of(
            "acme/telemetry/numeric/TelemetryFrame.hpp",
            "acme/telemetry/numeric/TelemetryFrame.cpp"));
  }

  /** Contract: collection slice fixtures generate deterministic C++ output. */
  @Test
  void cppGeneratorProducesDeterministicCollectionSliceOutput() throws Exception {
    assertFixtureMatchesGolden(
        "specs/collections-slice-valid.xml",
        tempDir.resolve("collections"),
        List.of(
            "acme/telemetry/collections/CollectionFrame.hpp",
            "acme/telemetry/collections/CollectionFrame.cpp"));
  }

  /**
   * Contract: the branch-heavy generator coverage fixture generates C++ successfully.
   *
   * <p>This fixture exercises many float/scaled/array/vector branches used by coverage gates.
   */
  @Test
  void cppGeneratorHandlesCoverageFixture() throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/java-generator-coverage-valid.xml");
    Path outputDirectory = tempDir.resolve("coverage");

    generator.generate(schema, outputDirectory);

    assertTrue(Files.exists(outputDirectory.resolve("acme/telemetry/coverage/ExtendedFrame.hpp")));
    assertTrue(Files.exists(outputDirectory.resolve("acme/telemetry/coverage/shared/Child.hpp")));
  }

  /** Contract: deferred conditional constructs fail with explicit unsupported diagnostics. */
  @Test
  void cppGeneratorFailsWithClearDiagnosticsForDeferredConditionalMembers() throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = compileFixture("specs/milestone-03-valid.xml");

    BmsException exception =
        assertThrows(
            BmsException.class, () -> generator.generate(schema, tempDir.resolve("deferred")));

    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_MEMBER")));
    assertTrue(
        exception.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().equals("GENERATOR_CPP_UNSUPPORTED_TYPE_REF")));
  }

  /** Contract: manual `uint64` scaled-int models hit both encode and decode code paths. */
  @Test
  void cppGeneratorEmitsUint64ScaledIntPaths() throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = schemaWithUint64ScaledIntMembers();
    Path outputDirectory = tempDir.resolve("uint64-scaled");

    generator.generate(schema, outputDirectory);

    Path sourcePath = outputDirectory.resolve("acme/telemetry/cppcoverage/Scaled64Frame.cpp");
    String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
    assertTrue(source.contains("scaleToUnsignedRaw<std::uint64_t>"));
    assertTrue(source.contains("readIntegral<std::uint64_t>(data, cursor"));
  }

  /**
   * Contract: invalid manual resolved models produce explicit unsupported diagnostics.
   *
   * <p>This intentionally bypasses semantic resolution to cover generator guard rails.
   */
  @Test
  void cppGeneratorReportsDetailedDiagnosticsForInvalidResolvedModels() {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = schemaWithUnsupportedResolvedMembers();

    BmsException exception =
        assertThrows(BmsException.class, () -> generator.generate(schema, tempDir.resolve("bad")));

    List<String> messages = exception.diagnostics().stream().map(Diagnostic::message).toList();
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingFloat")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingScaled")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingArray")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingVector")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingBlobArray")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("MissingBlobVector")));
    assertTrue(
        messages.stream()
            .anyMatch(
                message ->
                    message.contains("This type reference is not implemented in the C++ backend")));
    assertTrue(
        messages.stream().anyMatch(message -> message.contains("ResolvedArray(elementType=")));
    assertTrue(
        messages.stream().anyMatch(message -> message.contains("ResolvedVector(elementType=")));
    assertTrue(
        messages.stream().anyMatch(message -> message.contains("countField ref=\"missing\"")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("missing terminatorMatch")));
    assertTrue(
        messages.stream()
            .anyMatch(message -> message.contains("terminator modes require primitive element")));
    assertTrue(messages.stream().anyMatch(message -> message.contains("out of range: 999")));
    assertTrue(
        messages.stream().anyMatch(message -> message.contains("invalid terminator literal")));
  }

  /**
   * Builds a small resolved schema that exercises the `uint64` scaled-int encode/decode branches.
   *
   * @return resolved schema used only by this test
   */
  private static ResolvedSchema schemaWithUint64ScaledIntMembers() {
    ResolvedScaledInt reusableScaledInt =
        new ResolvedScaledInt(
            "ReusableUint64Scaled",
            PrimitiveType.UINT64,
            BigDecimal.ONE,
            Endian.BIG,
            "Reusable unsigned 64-bit scaled integer.");
    List<ResolvedMessageMember> members =
        List.of(
            new ResolvedScaledInt(
                "inlineUint64Scaled",
                PrimitiveType.UINT64,
                BigDecimal.ONE,
                Endian.BIG,
                "Inline unsigned 64-bit scaled integer."),
            new ResolvedField(
                "reusableUint64Scaled",
                new ScaledIntTypeRef("ReusableUint64Scaled"),
                null,
                Endian.BIG,
                null,
                "Field that references reusable uint64 scaled integer."));
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "Scaled64Frame",
            "Message for uint64 scaled-int branch coverage.",
            "acme.telemetry.cppcoverage",
            members);
    return new ResolvedSchema(
        "acme.telemetry.cppcoverage",
        List.of(messageType),
        List.of(),
        List.of(),
        List.of(reusableScaledInt));
  }

  /**
   * Builds a resolved schema with intentionally invalid references and member combinations.
   *
   * <p>The schema is not meant to pass semantic checks. It drives generator diagnostics paths.
   *
   * @return resolved schema with unsupported and invalid generator inputs
   */
  private static ResolvedSchema schemaWithUnsupportedResolvedMembers() {
    List<ResolvedMessageMember> members =
        List.of(
            new ResolvedField(
                "missingFloat",
                new FloatTypeRef("MissingFloat"),
                null,
                Endian.BIG,
                null,
                "Missing reusable float."),
            new ResolvedField(
                "missingScaled",
                new ScaledIntTypeRef("MissingScaled"),
                null,
                Endian.BIG,
                null,
                "Missing reusable scaled-int."),
            new ResolvedField(
                "missingArray",
                new ArrayTypeRef("MissingArray"),
                null,
                Endian.BIG,
                null,
                "Missing reusable array."),
            new ResolvedField(
                "missingVector",
                new VectorTypeRef("MissingVector"),
                null,
                Endian.BIG,
                null,
                "Missing reusable vector."),
            new ResolvedField(
                "missingBlobArray",
                new BlobArrayTypeRef("MissingBlobArray"),
                null,
                Endian.BIG,
                null,
                "Missing reusable blob array."),
            new ResolvedField(
                "missingBlobVector",
                new BlobVectorTypeRef("MissingBlobVector"),
                null,
                Endian.BIG,
                null,
                "Missing reusable blob vector."),
            new ResolvedField(
                "unsupportedVarStringRef",
                new VarStringTypeRef("LegacyVarString"),
                null,
                Endian.BIG,
                null,
                "Unsupported varString type reference."),
            new ResolvedArray(
                "arrayUnsupportedElement",
                new VarStringTypeRef("ElementVarString"),
                2,
                Endian.BIG,
                "Array with unsupported element type."),
            new ResolvedVector(
                "vectorUnsupportedElement",
                new BlobArrayTypeRef("NestedBlobArray"),
                Endian.BIG,
                "Vector with unsupported element type.",
                new ResolvedTerminatorValueLength("00")),
            new ResolvedVector(
                "vectorMissingCount",
                new PrimitiveTypeRef(PrimitiveType.UINT8),
                Endian.BIG,
                "Vector that references a missing count field.",
                new ResolvedCountFieldLength("missing")),
            new ResolvedVector(
                "vectorMissingTerminator",
                new PrimitiveTypeRef(PrimitiveType.UINT8),
                Endian.BIG,
                "Vector terminator field chain missing match leaf.",
                new ResolvedTerminatorField("root", null)),
            new ResolvedVector(
                "vectorNonPrimitiveTerminator",
                new MessageTypeRef("UnknownMessage"),
                Endian.BIG,
                "Terminated vector that uses non-primitive element type.",
                new ResolvedTerminatorValueLength("00")),
            new ResolvedVector(
                "vectorOutOfRangeTerminator",
                new PrimitiveTypeRef(PrimitiveType.UINT8),
                Endian.BIG,
                "Terminator literal outside primitive range.",
                new ResolvedTerminatorValueLength("999")),
            new ResolvedVector(
                "vectorInvalidTerminator",
                new PrimitiveTypeRef(PrimitiveType.UINT8),
                Endian.BIG,
                "Terminator literal that cannot be parsed.",
                new ResolvedTerminatorValueLength("0xZZ")),
            new ResolvedBlobVector(
                "blobVectorMissingCount",
                "Blob vector that references a missing count field.",
                new ResolvedCountFieldLength("missing")));
    ResolvedMessageType messageType =
        new ResolvedMessageType(
            "BrokenFrame",
            "Intentionally invalid resolved message used for generator diagnostics coverage.",
            "acme.telemetry.cppcoverage",
            members);
    return new ResolvedSchema("acme.telemetry.cppcoverage", List.of(messageType));
  }

  /**
   * Compiles one fixture and compares generated files against golden resources.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @param outputDirectory destination directory for generated C++ output
   * @param relativePaths generated file paths relative to the output root
   * @throws Exception if fixture compilation or output comparison fails
   */
  private void assertFixtureMatchesGolden(
      String fixtureResource, Path outputDirectory, List<String> relativePaths) throws Exception {
    CppCodeGenerator generator = new CppCodeGenerator();
    ResolvedSchema schema = compileFixture(fixtureResource);

    generator.generate(schema, outputDirectory);
    generator.generate(schema, outputDirectory);

    for (String relativePath : relativePaths) {
      Path generatedFile = outputDirectory.resolve(relativePath);
      assertTrue(Files.exists(generatedFile));
      assertEquals(
          TestSupport.readResource("golden/cpp/" + relativePath),
          Files.readString(generatedFile, StandardCharsets.UTF_8));
    }
  }

  /**
   * Compiles one XML fixture through the full front-end pipeline.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @return resolved schema for code generation
   * @throws Exception if compilation fails unexpectedly
   */
  private static ResolvedSchema compileFixture(String fixtureResource) throws Exception {
    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    return compiler.compile(TestSupport.resourcePath(fixtureResource));
  }
}
