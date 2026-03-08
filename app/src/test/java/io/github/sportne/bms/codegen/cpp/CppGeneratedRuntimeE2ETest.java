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

  /** Captures one process result for compile/runtime helper methods. */
  private record CommandResult(int exitCode, String output) {
    /** Creates one command-result record. */
    private CommandResult {}
  }
}
