package io.github.sportne.bms.codegen.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

/** Static-analysis tests for generated C++ code. */
class CppGeneratedStaticAnalysisTest {

  private static final Path CPP_STATIC_ANALYSIS_ARTIFACT_ROOT =
      Path.of(".tmp", "cpp-test-artifacts", "static-analysis").toAbsolutePath().normalize();

  private static final String CLANG_TIDY_CHECKS =
      "-*,"
          + "bugprone-*,"
          + "clang-analyzer-*,"
          + "cppcoreguidelines-*,"
          + "modernize-*,"
          + "performance-*,"
          + "readability-*,"
          + "portability-*,"
          + "-bugprone-easily-swappable-parameters,"
          + "-bugprone-standalone-empty,"
          + "-cppcoreguidelines-pro-bounds-array-to-pointer-decay,"
          + "-modernize-use-trailing-return-type,"
          + "-readability-braces-around-statements";

  /** Contract: static analysis runs for numeric generated C++ output. */
  @Test
  void generatedCppFromNumericSlicePassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/numeric-slice-valid.xml", "numeric");
  }

  /** Contract: static analysis runs for collection generated C++ output. */
  @Test
  void generatedCppFromCollectionSlicePassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/collections-slice-valid.xml", "collections");
  }

  /** Contract: static analysis runs for staged conditional generated C++ output. */
  @Test
  void generatedCppFromVarStringPadSlicePassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/varstring-pad-slice-valid.xml", "conditional-staged");
  }

  /** Contract: static analysis runs for backend conditional generated C++ output. */
  @Test
  void generatedCppFromConditionalBackendPassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/conditional-backend-valid.xml", "conditional-backend");
  }

  /** Contract: static analysis runs for relational conditional generated C++ output. */
  @Test
  void generatedCppFromConditionalRelationalPassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis(
        "specs/conditional-if-relational-valid.xml", "conditional-relational");
  }

  /** Contract: static analysis runs for crc32 generated C++ output. */
  @Test
  void generatedCppFromChecksumCrc32PassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/checksum-crc32-valid.xml", "checksum-crc32");
  }

  /** Contract: static analysis runs for crc64 generated C++ output. */
  @Test
  void generatedCppFromChecksumCrc64PassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/checksum-crc64-valid.xml", "checksum-crc64");
  }

  /** Contract: static analysis runs for sha256 generated C++ output. */
  @Test
  void generatedCppFromChecksumSha256PassesStaticAnalysis() throws Exception {
    assertFixturePassesStaticAnalysis("specs/checksum-sha256-valid.xml", "checksum-sha256");
  }

  /** Contract: probing one missing executable returns unavailable instead of failing the test. */
  @Test
  void missingExecutableIsReportedAsUnavailable() {
    assertFalse(
        hasExecutable("bms-executable-probe-9f3d0f56b3f44bfa93f53733cbf5582f"),
        "Expected missing executable probe to report unavailable.");
  }

  /**
   * Generates one fixture and runs clang-tidy/cppcheck over generated C++ output.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @param outputStem short output directory suffix for this fixture
   * @throws Exception if generation or static analysis fails
   */
  private void assertFixturePassesStaticAnalysis(String fixtureResource, String outputStem)
      throws Exception {
    assumeTrue(
        hasExecutable("clang-tidy"),
        "Skipping generated C++ static analysis: clang-tidy is not installed.");
    assumeTrue(
        hasExecutable("cppcheck"),
        "Skipping generated C++ static analysis: cppcheck is not installed.");

    Path fixtureDirectory = prepareFixtureDirectory(outputStem);
    ResolvedSchema schema = compileFixture(fixtureResource);
    CppCodeGenerator generator = new CppCodeGenerator();
    Path outputDirectory = fixtureDirectory.resolve("generated");
    generator.generate(schema, outputDirectory);

    List<Path> cppSources = collectCppSources(outputDirectory);
    assertTrue(!cppSources.isEmpty(), "Expected generated C++ source files for static analysis.");

    for (Path cppSource : cppSources) {
      List<String> clangTidyCommand = new ArrayList<>();
      clangTidyCommand.add("clang-tidy");
      clangTidyCommand.add(cppSource.toAbsolutePath().toString());
      clangTidyCommand.add("--checks=" + CLANG_TIDY_CHECKS);
      clangTidyCommand.add("--warnings-as-errors=bugprone-*,clang-analyzer-*,performance-*");
      clangTidyCommand.add("--extra-arg=-std=c++20");
      clangTidyCommand.add("--");
      clangTidyCommand.add("-I" + outputDirectory.toAbsolutePath());

      CommandResult clangTidyResult = runCommand(clangTidyCommand, fixtureDirectory);
      assertEquals(
          0,
          clangTidyResult.exitCode(),
          "clang-tidy failed for generated source " + cppSource + ".\n" + clangTidyResult.output());
    }

    List<String> cppcheckCommand = new ArrayList<>();
    cppcheckCommand.add("cppcheck");
    cppcheckCommand.add("--enable=warning,performance,portability,style");
    cppcheckCommand.add("--std=c++20");
    cppcheckCommand.add("--error-exitcode=2");
    cppcheckCommand.add("--inline-suppr");
    cppcheckCommand.add("--library=googletest");
    cppcheckCommand.add("--suppress=missingIncludeSystem");
    cppcheckCommand.add(outputDirectory.toAbsolutePath().toString());

    CommandResult cppcheckResult = runCommand(cppcheckCommand, fixtureDirectory);
    assertEquals(
        0,
        cppcheckResult.exitCode(),
        "cppcheck failed for generated sources in "
            + outputDirectory
            + ".\n"
            + cppcheckResult.output());
  }

  /**
   * Returns whether one executable can be launched from PATH.
   *
   * @param executable executable to probe
   * @return {@code true} when the executable is available
   */
  private static boolean hasExecutable(String executable) {
    try {
      CommandResult result = runCommand(List.of(executable, "--version"), Path.of("."));
      return result.exitCode() == 0;
    } catch (IllegalStateException exception) {
      return false;
    }
  }

  /**
   * Creates a clean fixture artifact directory inside the repo-local static-analysis root.
   *
   * @param outputStem short fixture identifier used as subdirectory name
   * @return clean directory path for one fixture run
   * @throws IOException if directory creation or cleanup fails
   */
  private static Path prepareFixtureDirectory(String outputStem) throws IOException {
    Files.createDirectories(CPP_STATIC_ANALYSIS_ARTIFACT_ROOT);
    Path fixtureDirectory = CPP_STATIC_ANALYSIS_ARTIFACT_ROOT.resolve(outputStem);
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
      pathStream
          .sorted(Comparator.reverseOrder())
          .forEach(CppGeneratedStaticAnalysisTest::deletePath);
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

  /** Captures one process result for static-analysis helper methods. */
  private record CommandResult(int exitCode, String output) {
    /** Creates one command-result record. */
    private CommandResult {}
  }
}
