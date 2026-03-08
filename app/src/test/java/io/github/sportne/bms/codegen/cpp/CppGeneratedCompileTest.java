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

/** Compile tests that verify generated C++ sources build with a C++20 compiler. */
class CppGeneratedCompileTest {

  private static final Path CPP_COMPILE_ARTIFACT_ROOT =
      Path.of(".tmp", "cpp-test-artifacts", "compile").toAbsolutePath().normalize();

  /** Contract: C++ code generated from the numeric slice fixture compiles cleanly. */
  @Test
  void generatedCppFromNumericSliceCompiles() throws Exception {
    assertFixtureCompiles("specs/numeric-slice-valid.xml", "numeric");
  }

  /** Contract: C++ code generated from the collection slice fixture compiles cleanly. */
  @Test
  void generatedCppFromCollectionSliceCompiles() throws Exception {
    assertFixtureCompiles("specs/collections-slice-valid.xml", "collections");
  }

  /** Contract: C++ code generated from the staged varString/pad fixture compiles cleanly. */
  @Test
  void generatedCppFromVarStringPadSliceCompiles() throws Exception {
    assertFixtureCompiles("specs/varstring-pad-slice-valid.xml", "conditional-staged");
  }

  /** Contract: C++ code generated from the checksum/if/nested fixture compiles cleanly. */
  @Test
  void generatedCppFromConditionalBackendFixtureCompiles() throws Exception {
    assertFixtureCompiles("specs/conditional-backend-valid.xml", "conditional-backend");
  }

  /** Contract: C++ code generated from relational/compound if fixtures compiles cleanly. */
  @Test
  void generatedCppFromConditionalRelationalFixtureCompiles() throws Exception {
    assertFixtureCompiles("specs/conditional-if-relational-valid.xml", "conditional-relational");
  }

  /** Contract: C++ code generated from the crc32 checksum fixture compiles cleanly. */
  @Test
  void generatedCppFromChecksumCrc32FixtureCompiles() throws Exception {
    assertFixtureCompiles("specs/checksum-crc32-valid.xml", "checksum-crc32");
  }

  /** Contract: C++ code generated from the crc64 checksum fixture compiles cleanly. */
  @Test
  void generatedCppFromChecksumCrc64FixtureCompiles() throws Exception {
    assertFixtureCompiles("specs/checksum-crc64-valid.xml", "checksum-crc64");
  }

  /** Contract: C++ code generated from the sha256 checksum fixture compiles cleanly. */
  @Test
  void generatedCppFromChecksumSha256FixtureCompiles() throws Exception {
    assertFixtureCompiles("specs/checksum-sha256-valid.xml", "checksum-sha256");
  }

  /**
   * Compiles generated C++ for one fixture.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @param outputStem short output directory suffix for this fixture
   * @throws Exception if generation or compilation fails unexpectedly
   */
  private void assertFixtureCompiles(String fixtureResource, String outputStem) throws Exception {
    Path fixtureDirectory = prepareFixtureDirectory(outputStem);
    ResolvedSchema schema = compileFixture(fixtureResource);
    CppCodeGenerator generator = new CppCodeGenerator();
    Path outputDirectory = fixtureDirectory.resolve("generated");
    generator.generate(schema, outputDirectory);

    List<Path> cppSources = collectCppSources(outputDirectory);
    assertTrue(!cppSources.isEmpty(), "Expected generated C++ source files to compile.");

    String compilerExecutable = requireCppCompiler();
    Path objectDirectory = fixtureDirectory.resolve("objects");
    Files.createDirectories(objectDirectory);
    List<String> command = new ArrayList<>();
    command.add(compilerExecutable);
    command.add("-std=c++20");
    command.add("-I" + outputDirectory.toAbsolutePath());
    command.add("-c");
    for (Path cppSource : cppSources) {
      command.add(cppSource.toAbsolutePath().toString());
    }

    CommandResult result = runCommand(command, objectDirectory);
    assertEquals(
        0,
        result.exitCode(),
        "Expected generated C++ sources to compile without errors.\n" + result.output());
  }

  /**
   * Creates a clean fixture artifact directory inside the repo-local C++ test artifacts root.
   *
   * @param outputStem short fixture identifier used as subdirectory name
   * @return clean directory path for one fixture run
   * @throws IOException if directory creation or cleanup fails
   */
  private static Path prepareFixtureDirectory(String outputStem) throws IOException {
    Files.createDirectories(CPP_COMPILE_ARTIFACT_ROOT);
    Path fixtureDirectory = CPP_COMPILE_ARTIFACT_ROOT.resolve(outputStem);
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
      pathStream.sorted(Comparator.reverseOrder()).forEach(CppGeneratedCompileTest::deletePath);
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
   * <p>This project requires C++ compile tests to fail when no compiler is available.
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

  /** Captures one process result for compile/runtime helper methods. */
  private record CommandResult(int exitCode, String output) {
    /** Creates one command-result record. */
    private CommandResult {}
  }
}
