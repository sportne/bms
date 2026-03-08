package io.github.sportne.bms.codegen.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runtime end-to-end tests for generated C++ code. */
class CppGeneratedRuntimeE2ETest {

  private static final Path CPP_RUNTIME_ARTIFACT_ROOT =
      Path.of(".tmp", "cpp-test-artifacts", "runtime").toAbsolutePath().normalize();

  /** Contract: numeric-slice generated C++ round-trips encode/decode correctly at runtime. */
  @Test
  void generatedCppNumericSliceRoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/numeric-slice-valid.xml", "numeric", numericRuntimeHarness(), "numeric_runtime");
  }

  /** Contract: collection-slice generated C++ round-trips encode/decode correctly at runtime. */
  @Test
  void generatedCppCollectionSliceRoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/collections-slice-valid.xml",
        "collections",
        collectionsRuntimeHarness(),
        "collections_runtime");
  }

  /** Contract: staged varString/pad generated C++ round-trips correctly at runtime. */
  @Test
  void generatedCppVarStringPadSliceRoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/varstring-pad-slice-valid.xml",
        "conditional_staged",
        varStringPadRuntimeHarness(),
        "conditional_staged_runtime");
  }

  /** Contract: checksum/if/nested generated C++ round-trips correctly at runtime. */
  @Test
  void generatedCppConditionalBackendRoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/conditional-backend-valid.xml",
        "conditional_backend",
        conditionalBackendRuntimeHarness(),
        "conditional_backend_runtime");
  }

  /** Contract: checksum mismatch paths throw and are observable at generated runtime. */
  @Test
  void generatedCppConditionalBackendChecksumMismatchFails() throws Exception {
    assertRuntimeRoundTrip(
        "specs/conditional-backend-valid.xml",
        "conditional_backend_mismatch",
        conditionalBackendChecksumMismatchHarness(),
        "conditional_backend_mismatch_runtime");
  }

  /** Contract: relational/compound conditional generated C++ round-trips true/false branches. */
  @Test
  void generatedCppConditionalRelationalRoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/conditional-if-relational-valid.xml",
        "conditional_relational",
        conditionalRelationalRuntimeHarness(),
        "conditional_relational_runtime");
  }

  /** Contract: generated C++ round-trips the crc32 checksum fixture. */
  @Test
  void generatedCppChecksumCrc32RoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/checksum-crc32-valid.xml",
        "checksum_crc32",
        checksumCrc32RuntimeHarness(),
        "checksum_crc32_runtime");
  }

  /** Contract: generated C++ round-trips the crc64 checksum fixture. */
  @Test
  void generatedCppChecksumCrc64RoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/checksum-crc64-valid.xml",
        "checksum_crc64",
        checksumCrc64RuntimeHarness(),
        "checksum_crc64_runtime");
  }

  /** Contract: generated C++ round-trips the sha256 checksum fixture. */
  @Test
  void generatedCppChecksumSha256RoundTrips() throws Exception {
    assertRuntimeRoundTrip(
        "specs/checksum-sha256-valid.xml",
        "checksum_sha256",
        checksumSha256RuntimeHarness(),
        "checksum_sha256_runtime");
  }

  /**
   * Generates C++ for one fixture, compiles a runtime harness, and executes it.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @param outputStem short output directory suffix for this fixture
   * @param harnessSource C++ runtime harness source text
   * @param executableName executable filename stem
   * @throws Exception if generation, compilation, or runtime execution fails
   */
  private void assertRuntimeRoundTrip(
      String fixtureResource, String outputStem, String harnessSource, String executableName)
      throws Exception {
    Path fixtureDirectory = prepareFixtureDirectory(outputStem);
    ResolvedSchema schema = compileFixture(fixtureResource);
    CppCodeGenerator generator = new CppCodeGenerator();
    Path outputDirectory = fixtureDirectory.resolve("generated");
    generator.generate(schema, outputDirectory);

    List<Path> cppSources = collectCppSources(outputDirectory);
    assertTrue(!cppSources.isEmpty(), "Expected generated C++ source files for runtime testing.");

    Path harnessPath = fixtureDirectory.resolve(executableName + ".cpp");
    Files.writeString(harnessPath, harnessSource, StandardCharsets.UTF_8);

    String compilerExecutable = requireCppCompiler();
    Path executablePath = fixtureDirectory.resolve(executableName);
    List<String> compileCommand = new ArrayList<>();
    compileCommand.add(compilerExecutable);
    compileCommand.add("-std=c++20");
    compileCommand.add("-I" + outputDirectory.toAbsolutePath());
    for (Path cppSource : cppSources) {
      compileCommand.add(cppSource.toAbsolutePath().toString());
    }
    compileCommand.add(harnessPath.toAbsolutePath().toString());
    compileCommand.add("-o");
    compileCommand.add(executablePath.toAbsolutePath().toString());

    CommandResult compileResult = runCommand(compileCommand, fixtureDirectory);
    assertEquals(
        0,
        compileResult.exitCode(),
        "Expected generated C++ runtime harness to compile.\n" + compileResult.output());

    CommandResult runtimeResult =
        runCommand(List.of(executablePath.toAbsolutePath().toString()), fixtureDirectory);
    assertEquals(
        0,
        runtimeResult.exitCode(),
        "Expected generated C++ runtime harness to pass roundtrip checks.\n"
            + runtimeResult.output());
  }

  /**
   * Creates a clean fixture artifact directory inside the repo-local C++ runtime artifacts root.
   *
   * @param outputStem short fixture identifier used as subdirectory name
   * @return clean directory path for one fixture run
   * @throws IOException if directory creation or cleanup fails
   */
  private static Path prepareFixtureDirectory(String outputStem) throws IOException {
    Files.createDirectories(CPP_RUNTIME_ARTIFACT_ROOT);
    Path fixtureDirectory = CPP_RUNTIME_ARTIFACT_ROOT.resolve(outputStem);
    deleteDirectoryIfExists(fixtureDirectory);
    Files.createDirectories(fixtureDirectory);
    return fixtureDirectory;
  }

  /**
   * Deletes one directory tree when it already exists.
   *
   * @param directory directory path to delete recursively
   * @throws IOException if recursive deletion fails
   */
  private static void deleteDirectoryIfExists(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (var pathStream = Files.walk(directory)) {
      pathStream.sorted(Comparator.reverseOrder()).forEach(CppGeneratedRuntimeE2ETest::deletePath);
    }
  }

  /**
   * Deletes one filesystem path and wraps checked errors in an unchecked exception.
   *
   * @param path file or directory path to delete
   */
  private static void deletePath(Path path) {
    try {
      Files.delete(path);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to delete test artifact path: " + path, exception);
    }
  }

  /**
   * Collects generated `.cpp` files in deterministic path order.
   *
   * @param outputDirectory generated C++ output root
   * @return sorted `.cpp` source file list
   * @throws IOException if directory traversal fails
   */
  private static List<Path> collectCppSources(Path outputDirectory) throws IOException {
    try (var pathStream = Files.walk(outputDirectory)) {
      return pathStream
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".cpp"))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  /**
   * Returns a working C++ compiler executable name.
   *
   * <p>This project requires C++ runtime tests to fail when no compiler is available.
   *
   * @return compiler executable name (`g++` or `clang++`)
   */
  private static String requireCppCompiler() {
    for (String candidate : List.of("g++", "clang++")) {
      CommandResult result = runCommand(List.of(candidate, "--version"), Path.of("."));
      if (result.exitCode() == 0) {
        return candidate;
      }
    }
    throw new AssertionError("No C++ compiler was found. Install g++ or clang++ to run this test.");
  }

  /**
   * Runs one process and captures combined stdout/stderr text.
   *
   * @param command process command tokens
   * @param workdir working directory for the process
   * @return process exit code and text output
   */
  private static CommandResult runCommand(List<String> command, Path workdir) {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(workdir.toFile());
    processBuilder.redirectErrorStream(true);
    try {
      Process process = processBuilder.start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      int exitCode = process.waitFor();
      return new CommandResult(exitCode, output);
    } catch (IOException | InterruptedException exception) {
      throw new IllegalStateException(
          "Failed to run process: " + String.join(" ", command), exception);
    }
  }

  /**
   * Compiles one XML fixture through the full BMS front-end pipeline.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @return resolved schema used by generators
   * @throws Exception if fixture compilation fails unexpectedly
   */
  private static ResolvedSchema compileFixture(String fixtureResource) throws Exception {
    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    return compiler.compile(TestSupport.resourcePath(fixtureResource));
  }

  /**
   * Returns runtime C++ harness source for numeric-slice roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String numericRuntimeHarness() {
    return """
        #include \"acme/telemetry/numeric/TelemetryFrame.hpp\"

        #include <cmath>
        #include <cstdint>

        int main() {
          using acme::telemetry::numeric::TelemetryFrame;

          TelemetryFrame source{};
          source.version = static_cast<std::uint8_t>(2);
          source.setStatusBitsAlarm(true);
          source.setStatusBitsState(2ULL);
          source.temperature = 25.3;
          source.voltage = 12.34;
          source.reusableTemperature = -1.25;
          source.reusableFloat = 1.5;

          std::vector<std::uint8_t> encoded = source.encode();
          TelemetryFrame decoded = TelemetryFrame::decode(encoded);

          if (decoded.version != static_cast<std::uint8_t>(2)) {
            return 1;
          }
          if (!decoded.getStatusBitsAlarm()) {
            return 2;
          }
          if (decoded.getStatusBitsState() != 2ULL) {
            return 3;
          }
          if (std::abs(decoded.temperature - 25.3) > 0.11) {
            return 4;
          }
          if (std::abs(decoded.voltage - 12.34) > 0.02) {
            return 5;
          }
          if (std::abs(decoded.reusableTemperature + 1.25) > 0.02) {
            return 6;
          }
          if (std::abs(decoded.reusableFloat - 1.5) > 0.0001) {
            return 7;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for collection-slice roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String collectionsRuntimeHarness() {
    return """
        #include \"acme/telemetry/collections/CollectionFrame.hpp\"

        #include <array>
        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::collections::CollectionFrame;

          CollectionFrame source{};
          source.count = static_cast<std::uint16_t>(3);
          source.blobCount = static_cast<std::uint8_t>(2);
          source.samples = std::array<std::uint8_t, 4>{1U, 2U, 3U, 4U};
          source.events = std::vector<std::uint8_t>{5U, 6U, 7U};
          source.hash = std::array<std::uint8_t, 8>{0U, 1U, 2U, 3U, 4U, 5U, 6U, 7U};
          source.payload = std::vector<std::uint8_t>{9U, 10U};
          source.pathData = std::vector<std::uint8_t>{11U, 12U};
          source.reusableArrayField = std::array<std::uint8_t, 2>{13U, 14U};
          source.reusableVectorField = std::vector<std::uint8_t>{21U, 22U, 23U};
          source.reusableBlobArrayField =
              std::array<std::uint8_t, 16>{
                  31U,
                  32U,
                  33U,
                  34U,
                  35U,
                  36U,
                  37U,
                  38U,
                  39U,
                  40U,
                  41U,
                  42U,
                  43U,
                  44U,
                  45U,
                  46U};
          source.reusableBlobVectorField = std::vector<std::uint8_t>{51U, 52U, 53U};

          std::vector<std::uint8_t> encoded = source.encode();
          CollectionFrame decoded = CollectionFrame::decode(encoded);

          if (decoded.count != source.count) {
            return 1;
          }
          if (decoded.blobCount != source.blobCount) {
            return 2;
          }
          if (decoded.samples != source.samples) {
            return 3;
          }
          if (decoded.events != source.events) {
            return 4;
          }
          if (decoded.hash != source.hash) {
            return 5;
          }
          if (decoded.payload != source.payload) {
            return 6;
          }
          if (decoded.pathData != source.pathData) {
            return 7;
          }
          if (decoded.reusableArrayField != source.reusableArrayField) {
            return 8;
          }
          if (decoded.reusableVectorField != source.reusableVectorField) {
            return 9;
          }
          if (decoded.reusableBlobArrayField != source.reusableBlobArrayField) {
            return 10;
          }
          if (decoded.reusableBlobVectorField != source.reusableBlobVectorField) {
            return 11;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for varString/pad slice roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String varStringPadRuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/ConditionalFrame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::ConditionalFrame;

          ConditionalFrame source{};
          source.nameLength = static_cast<std::uint8_t>(4);
          source.reusableLength = static_cast<std::uint16_t>(5);
          source.inlineName = "ABCD";
          source.inlineTag = "TAG";
          source.reusableLabel = "HELLO";

          std::vector<std::uint8_t> encoded = source.encode();
          ConditionalFrame decoded = ConditionalFrame::decode(encoded);

          if (decoded.nameLength != source.nameLength) {
            return 1;
          }
          if (decoded.reusableLength != source.reusableLength) {
            return 2;
          }
          if (decoded.inlineName != source.inlineName) {
            return 3;
          }
          if (decoded.inlineTag != source.inlineTag) {
            return 4;
          }
          if (decoded.reusableLabel != source.reusableLabel) {
            return 5;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for checksum/if/nested roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String conditionalBackendRuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/backend/ConditionalBackendFrame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::backend::ConditionalBackendFrame;

          ConditionalBackendFrame versionOne{};
          versionOne.version = static_cast<std::uint8_t>(1);
          versionOne.payload = static_cast<std::uint8_t>(9);
          versionOne.modeValue = static_cast<std::uint8_t>(7);
          versionOne.nestedValue = static_cast<std::uint16_t>(4660);
          versionOne.alwaysValue = static_cast<std::uint8_t>(3);

          std::vector<std::uint8_t> encodedOne = versionOne.encode();
          ConditionalBackendFrame decodedOne = ConditionalBackendFrame::decode(encodedOne);

          if (decodedOne.version != versionOne.version) {
            return 1;
          }
          if (decodedOne.payload != versionOne.payload) {
            return 2;
          }
          if (decodedOne.modeValue != versionOne.modeValue) {
            return 3;
          }
          if (decodedOne.nestedValue != versionOne.nestedValue) {
            return 4;
          }
          if (decodedOne.alwaysValue != versionOne.alwaysValue) {
            return 5;
          }

          ConditionalBackendFrame versionTwo{};
          versionTwo.version = static_cast<std::uint8_t>(2);
          versionTwo.payload = static_cast<std::uint8_t>(1);
          versionTwo.modeValue = static_cast<std::uint8_t>(99);
          versionTwo.nestedValue = static_cast<std::uint16_t>(9999);
          versionTwo.alwaysValue = static_cast<std::uint8_t>(4);

          std::vector<std::uint8_t> encodedTwo = versionTwo.encode();
          ConditionalBackendFrame decodedTwo = ConditionalBackendFrame::decode(encodedTwo);

          if (decodedTwo.version != versionTwo.version) {
            return 6;
          }
          if (decodedTwo.payload != versionTwo.payload) {
            return 7;
          }
          if (decodedTwo.modeValue != static_cast<std::uint8_t>(0)) {
            return 8;
          }
          if (decodedTwo.nestedValue != static_cast<std::uint16_t>(0)) {
            return 9;
          }
          if (decodedTwo.alwaysValue != versionTwo.alwaysValue) {
            return 10;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for checksum mismatch behavior checks.
   *
   * @return runtime harness source text
   */
  private static String conditionalBackendChecksumMismatchHarness() {
    return """
        #include \"acme/telemetry/conditional/backend/ConditionalBackendFrame.hpp\"

        #include <cstdint>
        #include <stdexcept>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::backend::ConditionalBackendFrame;

          ConditionalBackendFrame source{};
          source.version = static_cast<std::uint8_t>(1);
          source.payload = static_cast<std::uint8_t>(2);
          source.modeValue = static_cast<std::uint8_t>(3);
          source.nestedValue = static_cast<std::uint16_t>(4);
          source.alwaysValue = static_cast<std::uint8_t>(5);

          std::vector<std::uint8_t> encoded = source.encode();
          encoded[0] = static_cast<std::uint8_t>(encoded[0] ^ 0x01U);

          try {
            (void) ConditionalBackendFrame::decode(encoded);
            return 1;
          } catch (const std::invalid_argument&) {
            return 0;
          } catch (...) {
            return 2;
          }
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for relational/compound condition checks.
   *
   * @return runtime harness source text
   */
  private static String conditionalRelationalRuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/relational/ConditionalRelationalFrame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::relational::ConditionalRelationalFrame;

          ConditionalRelationalFrame source{};
          source.version = static_cast<std::uint8_t>(2);
          source.ltMode = static_cast<std::uint8_t>(11);
          source.lteMode = static_cast<std::uint8_t>(12);
          source.gtMode = static_cast<std::uint8_t>(13);
          source.gteMode = static_cast<std::uint8_t>(14);
          source.betweenMode = static_cast<std::uint8_t>(15);
          source.compoundMode = static_cast<std::uint8_t>(16);

          std::vector<std::uint8_t> encoded = source.encode();
          ConditionalRelationalFrame decoded = ConditionalRelationalFrame::decode(encoded);

          if (decoded.version != source.version) {
            return 1;
          }
          if (decoded.ltMode != source.ltMode) {
            return 2;
          }
          if (decoded.lteMode != source.lteMode) {
            return 3;
          }
          if (decoded.gtMode != source.gtMode) {
            return 4;
          }
          if (decoded.gteMode != source.gteMode) {
            return 5;
          }
          if (decoded.betweenMode != source.betweenMode) {
            return 6;
          }
          if (decoded.compoundMode != source.compoundMode) {
            return 7;
          }

          ConditionalRelationalFrame sourceFalse{};
          sourceFalse.version = static_cast<std::uint8_t>(0);
          sourceFalse.ltMode = static_cast<std::uint8_t>(1);
          sourceFalse.lteMode = static_cast<std::uint8_t>(2);
          sourceFalse.gtMode = static_cast<std::uint8_t>(3);
          sourceFalse.gteMode = static_cast<std::uint8_t>(4);
          sourceFalse.betweenMode = static_cast<std::uint8_t>(5);
          sourceFalse.compoundMode = static_cast<std::uint8_t>(6);

          std::vector<std::uint8_t> encodedFalse = sourceFalse.encode();
          ConditionalRelationalFrame decodedFalse = ConditionalRelationalFrame::decode(encodedFalse);

          if (decodedFalse.version != sourceFalse.version) {
            return 8;
          }
          if (decodedFalse.ltMode != sourceFalse.ltMode) {
            return 9;
          }
          if (decodedFalse.lteMode != sourceFalse.lteMode) {
            return 10;
          }
          if (decodedFalse.gtMode != static_cast<std::uint8_t>(0)) {
            return 11;
          }
          if (decodedFalse.gteMode != static_cast<std::uint8_t>(0)) {
            return 12;
          }
          if (decodedFalse.betweenMode != static_cast<std::uint8_t>(0)) {
            return 13;
          }
          if (decodedFalse.compoundMode != static_cast<std::uint8_t>(0)) {
            return 14;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for crc32 checksum roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String checksumCrc32RuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/algorithms/ChecksumCrc32Frame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::algorithms::ChecksumCrc32Frame;

          ChecksumCrc32Frame source{};
          source.version = static_cast<std::uint8_t>(7);
          source.payload = static_cast<std::uint8_t>(9);

          std::vector<std::uint8_t> encoded = source.encode();
          ChecksumCrc32Frame decoded = ChecksumCrc32Frame::decode(encoded);

          if (decoded.version != source.version) {
            return 1;
          }
          if (decoded.payload != source.payload) {
            return 2;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for crc64 checksum roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String checksumCrc64RuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/algorithms/ChecksumCrc64Frame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::algorithms::ChecksumCrc64Frame;

          ChecksumCrc64Frame source{};
          source.version = static_cast<std::uint8_t>(10);
          source.payload = static_cast<std::uint8_t>(11);

          std::vector<std::uint8_t> encoded = source.encode();
          ChecksumCrc64Frame decoded = ChecksumCrc64Frame::decode(encoded);

          if (decoded.version != source.version) {
            return 1;
          }
          if (decoded.payload != source.payload) {
            return 2;
          }
          return 0;
        }
        """;
  }

  /**
   * Returns runtime C++ harness source for sha256 checksum roundtrip checks.
   *
   * @return runtime harness source text
   */
  private static String checksumSha256RuntimeHarness() {
    return """
        #include \"acme/telemetry/conditional/algorithms/ChecksumSha256Frame.hpp\"

        #include <cstdint>
        #include <vector>

        int main() {
          using acme::telemetry::conditional::algorithms::ChecksumSha256Frame;

          ChecksumSha256Frame source{};
          source.version = static_cast<std::uint8_t>(12);
          source.payload = static_cast<std::uint8_t>(13);

          std::vector<std::uint8_t> encoded = source.encode();
          ChecksumSha256Frame decoded = ChecksumSha256Frame::decode(encoded);

          if (decoded.version != source.version) {
            return 1;
          }
          if (decoded.payload != source.payload) {
            return 2;
          }
          return 0;
        }
        """;
  }

  /** Captures one process result for compile/runtime helper methods. */
  private record CommandResult(int exitCode, String output) {
    /** Creates one command-result record. */
    private CommandResult {}
  }
}
