package io.github.sportne.bms.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.codegen.cpp.CppCodeGenerator;
import io.github.sportne.bms.codegen.java.JavaCodeGenerator;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Shared helper methods for cross-language conformance tests. */
final class ConformanceRuntimeSupport {

  static final Path CONFORMANCE_ARTIFACT_ROOT =
      Path.of(".tmp", "cross-language-conformance").toAbsolutePath().normalize();

  /** Utility class; not meant to be instantiated. */
  private ConformanceRuntimeSupport() {}

  /**
   * Creates a clean directory for one conformance test case.
   *
   * @param caseId stable case identifier
   * @return clean output directory for the case
   * @throws IOException when filesystem operations fail
   */
  static Path prepareCaseDirectory(String caseId) throws IOException {
    Files.createDirectories(CONFORMANCE_ARTIFACT_ROOT);
    Path caseDirectory = CONFORMANCE_ARTIFACT_ROOT.resolve(caseId);
    deleteDirectoryIfExists(caseDirectory);
    Files.createDirectories(caseDirectory);
    return caseDirectory;
  }

  /**
   * Deletes one directory tree when it already exists.
   *
   * @param directory directory path to delete recursively
   * @throws IOException when recursive deletion fails
   */
  private static void deleteDirectoryIfExists(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      return;
    }
    try (var pathStream = Files.walk(directory)) {
      pathStream.sorted(Comparator.reverseOrder()).forEach(ConformanceRuntimeSupport::deletePath);
    }
  }

  /**
   * Deletes one filesystem path and wraps checked failures.
   *
   * @param path file or directory path to delete
   */
  private static void deletePath(Path path) {
    try {
      Files.delete(path);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to delete path: " + path, exception);
    }
  }

  /**
   * Compiles one fixture through the full front-end pipeline.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @return resolved schema produced by the front-end compiler
   * @throws Exception when fixture compilation fails
   */
  static ResolvedSchema compileFixture(String fixtureResource) throws Exception {
    BmsCompiler compiler = new BmsCompiler(TestSupport.repositoryXsdPath());
    return compiler.compile(TestSupport.resourcePath(fixtureResource));
  }

  /**
   * Generates Java and C++ output for one resolved schema.
   *
   * @param schema resolved schema input
   * @param javaOutputDirectory generated Java output directory
   * @param cppOutputDirectory generated C++ output directory
   */
  static void generateBothTargets(
      ResolvedSchema schema, Path javaOutputDirectory, Path cppOutputDirectory) throws Exception {
    new JavaCodeGenerator().generate(schema, javaOutputDirectory);
    new CppCodeGenerator().generate(schema, cppOutputDirectory);
  }

  /**
   * Compiles generated Java sources and returns a class loader.
   *
   * @param sourceDirectory generated Java source directory
   * @param classDirectory output directory for compiled `.class` files
   * @return class loader for compiled generated classes
   * @throws Exception when Java compilation fails
   */
  static URLClassLoader compileGeneratedJavaSources(Path sourceDirectory, Path classDirectory)
      throws Exception {
    List<Path> javaSources = collectJavaSources(sourceDirectory);
    assertNotNull(javaSources);
    if (javaSources.isEmpty()) {
      throw new AssertionError("Expected generated Java source files.");
    }
    Files.createDirectories(classDirectory);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new AssertionError("System JavaCompiler is required for conformance tests.");
    }

    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromPaths(javaSources);
      List<String> options = List.of("--release", "21", "-d", classDirectory.toString());
      Boolean success =
          compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits).call();
      if (!Boolean.TRUE.equals(success)) {
        String diagnosticText =
            diagnostics.getDiagnostics().stream()
                .map(Object::toString)
                .collect(Collectors.joining("\n"));
        throw new AssertionError("Generated Java compilation failed:\n" + diagnosticText);
      }
    }
    URL classDirectoryUrl = classDirectory.toUri().toURL();
    return AccessController.doPrivileged(
        (PrivilegedAction<URLClassLoader>) () -> new URLClassLoader(new URL[] {classDirectoryUrl}));
  }

  /**
   * Collects Java source files under one generated directory.
   *
   * @param sourceDirectory generated Java source directory
   * @return sorted Java source paths
   * @throws IOException when directory traversal fails
   */
  private static List<Path> collectJavaSources(Path sourceDirectory) throws IOException {
    try (var pathStream = Files.walk(sourceDirectory)) {
      return pathStream
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .sorted()
          .toList();
    }
  }

  /**
   * Compiles one C++ harness together with generated C++ sources.
   *
   * @param caseDirectory output directory for one case
   * @param generatedCppDirectory generated C++ source directory
   * @param harnessSource harness source text
   * @param executableName executable file name
   * @return path to compiled executable
   * @throws IOException when filesystem operations fail
   */
  static Path compileCppHarness(
      Path caseDirectory, Path generatedCppDirectory, String harnessSource, String executableName)
      throws IOException {
    List<Path> cppSources = collectCppSources(generatedCppDirectory);
    if (cppSources.isEmpty()) {
      throw new AssertionError("Expected generated C++ source files.");
    }

    Path harnessPath = caseDirectory.resolve(executableName + ".cpp");
    Files.writeString(harnessPath, harnessSource, StandardCharsets.UTF_8);

    Path executablePath = caseDirectory.resolve(executableName);
    List<String> command = new ArrayList<>();
    command.add(requireCppCompiler());
    command.add("-std=c++20");
    command.add("-I" + generatedCppDirectory.toAbsolutePath());
    for (Path cppSource : cppSources) {
      command.add(cppSource.toAbsolutePath().toString());
    }
    command.add(harnessPath.toAbsolutePath().toString());
    command.add("-o");
    command.add(executablePath.toAbsolutePath().toString());

    CommandResult compileResult = runCommand(command, caseDirectory);
    assertEquals(
        0, compileResult.exitCode(), "C++ harness compile failed.\n" + compileResult.output());
    return executablePath;
  }

  /**
   * Collects generated C++ source files under one directory.
   *
   * @param sourceDirectory generated C++ source directory
   * @return sorted C++ source paths
   * @throws IOException when directory traversal fails
   */
  private static List<Path> collectCppSources(Path sourceDirectory) throws IOException {
    try (var pathStream = Files.walk(sourceDirectory)) {
      return pathStream
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".cpp"))
          .sorted()
          .toList();
    }
  }

  /**
   * Returns one usable C++ compiler executable.
   *
   * @return compiler executable name (`g++` or `clang++`)
   */
  static String requireCppCompiler() {
    for (String candidate : List.of("g++", "clang++")) {
      CommandResult result = runCommand(List.of(candidate, "--version"), Path.of("."));
      if (result.exitCode() == 0) {
        return candidate;
      }
    }
    throw new AssertionError("No C++ compiler was found. Install g++ or clang++.");
  }

  /**
   * Runs one process and captures combined stdout and stderr text.
   *
   * @param command process command tokens
   * @param workdir process working directory
   * @return process exit code and combined output text
   */
  static CommandResult runCommand(List<String> command, Path workdir) {
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
   * Runs C++ harness in encode mode and returns encoded bytes.
   *
   * @param executable compiled harness executable path
   * @param workdir process working directory
   * @return encoded bytes emitted by C++ harness
   */
  static byte[] runCppEncode(Path executable, Path workdir) {
    CommandResult result =
        runCommand(List.of(executable.toAbsolutePath().toString(), "encode"), workdir);
    assertEquals(0, result.exitCode(), "C++ encode mode failed.\n" + result.output());
    return hexToBytes(result.output().trim());
  }

  /**
   * Runs C++ harness in decode-assert mode with one encoded byte array.
   *
   * @param executable compiled harness executable path
   * @param workdir process working directory
   * @param bytes encoded bytes to decode and assert in C++
   */
  static void runCppDecodeAssert(Path executable, Path workdir, byte[] bytes) {
    String hexInput = bytesToHex(bytes);
    CommandResult result =
        runCommand(
            List.of(executable.toAbsolutePath().toString(), "decodeAssert", hexInput), workdir);
    assertEquals(0, result.exitCode(), "C++ decode assert mode failed.\n" + result.output());
  }

  /**
   * Converts bytes to lowercase hexadecimal text.
   *
   * @param bytes input bytes
   * @return lowercase hex text
   */
  static String bytesToHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes).toLowerCase(Locale.ROOT);
  }

  /**
   * Converts hexadecimal text to bytes.
   *
   * @param hex lowercase or uppercase hex text
   * @return decoded byte array
   */
  static byte[] hexToBytes(String hex) {
    return HexFormat.of().parseHex(hex);
  }

  /**
   * Creates one generated message instance through reflection.
   *
   * @param classLoader generated-class class loader
   * @param className fully qualified generated class name
   * @return new message instance
   * @throws Exception when reflection construction fails
   */
  static Object newInstance(ClassLoader classLoader, String className) throws Exception {
    Class<?> type = classLoader.loadClass(className);
    return type.getDeclaredConstructor().newInstance();
  }

  /**
   * Sets one public field value through reflection.
   *
   * @param target target object
   * @param fieldName field name
   * @param value field value
   * @throws Exception when reflection access fails
   */
  static void setFieldValue(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getField(fieldName);
    field.set(target, value);
  }

  /**
   * Reads one public field value through reflection.
   *
   * @param target target object
   * @param fieldName field name
   * @return field value
   * @throws Exception when reflection access fails
   */
  static Object getFieldValue(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getField(fieldName);
    return field.get(target);
  }

  /**
   * Sets one numeric field through reflection, adapting to the field primitive type.
   *
   * @param target target object
   * @param fieldName field name
   * @param value numeric value
   * @throws Exception when reflection access fails
   */
  static void setNumericField(Object target, String fieldName, long value) throws Exception {
    Field field = target.getClass().getField(fieldName);
    Class<?> type = field.getType();
    if (type == byte.class) {
      field.setByte(target, (byte) value);
      return;
    }
    if (type == short.class) {
      field.setShort(target, (short) value);
      return;
    }
    if (type == int.class) {
      field.setInt(target, (int) value);
      return;
    }
    if (type == long.class) {
      field.setLong(target, value);
      return;
    }
    throw new IllegalArgumentException(
        "Unsupported numeric field type: " + type + " for " + fieldName);
  }

  /**
   * Sets one floating field through reflection.
   *
   * @param target target object
   * @param fieldName field name
   * @param value floating value
   * @throws Exception when reflection access fails
   */
  static void setFloatingField(Object target, String fieldName, double value) throws Exception {
    Field field = target.getClass().getField(fieldName);
    Class<?> type = field.getType();
    if (type == float.class) {
      field.setFloat(target, (float) value);
      return;
    }
    if (type == double.class) {
      field.setDouble(target, value);
      return;
    }
    throw new IllegalArgumentException(
        "Unsupported floating field type: " + type + " for " + fieldName);
  }

  /**
   * Sets one numeric array field through reflection.
   *
   * @param target target object
   * @param fieldName array field name
   * @param values numeric values to copy
   * @throws Exception when reflection access fails
   */
  static void setNumericArrayField(Object target, String fieldName, long[] values)
      throws Exception {
    Field field = target.getClass().getField(fieldName);
    Class<?> componentType = field.getType().getComponentType();
    Object array = Array.newInstance(componentType, values.length);
    for (int index = 0; index < values.length; index++) {
      setNumericArrayValue(array, componentType, index, values[index]);
    }
    field.set(target, array);
  }

  /**
   * Sets one object-array field through reflection.
   *
   * @param target target object
   * @param fieldName array field name
   * @param values object values to copy
   * @throws Exception when reflection access fails
   */
  static void setObjectArrayField(Object target, String fieldName, Object[] values)
      throws Exception {
    Field field = target.getClass().getField(fieldName);
    Class<?> componentType = field.getType().getComponentType();
    Object array = Array.newInstance(componentType, values.length);
    for (int index = 0; index < values.length; index++) {
      Array.set(array, index, values[index]);
    }
    field.set(target, array);
  }

  /**
   * Sets one byte-array field through reflection.
   *
   * @param target target object
   * @param fieldName byte-array field name
   * @param values byte values to copy
   * @throws Exception when reflection access fails
   */
  static void setByteArrayField(Object target, String fieldName, byte[] values) throws Exception {
    setFieldValue(target, fieldName, values.clone());
  }

  /**
   * Sets one numeric element inside a reflected primitive array.
   *
   * @param array target array
   * @param componentType array component type
   * @param index destination index
   * @param value numeric value
   */
  private static void setNumericArrayValue(
      Object array, Class<?> componentType, int index, long value) {
    if (componentType == byte.class) {
      Array.setByte(array, index, (byte) value);
      return;
    }
    if (componentType == short.class) {
      Array.setShort(array, index, (short) value);
      return;
    }
    if (componentType == int.class) {
      Array.setInt(array, index, (int) value);
      return;
    }
    if (componentType == long.class) {
      Array.setLong(array, index, value);
      return;
    }
    if (componentType == float.class) {
      Array.setFloat(array, index, (float) value);
      return;
    }
    if (componentType == double.class) {
      Array.setDouble(array, index, value);
      return;
    }
    throw new IllegalArgumentException("Unsupported numeric array type: " + componentType);
  }

  /**
   * Asserts one decoded numeric field value.
   *
   * @param target decoded object
   * @param fieldName numeric field name
   * @param expected expected numeric value
   * @throws Exception when reflection access fails
   */
  static void assertNumericFieldEquals(Object target, String fieldName, long expected)
      throws Exception {
    Number value = (Number) getFieldValue(target, fieldName);
    assertEquals(expected, value.longValue(), "Field mismatch: " + fieldName);
  }

  /**
   * Asserts one decoded floating field value with tolerance.
   *
   * @param target decoded object
   * @param fieldName floating field name
   * @param expected expected floating value
   * @param tolerance absolute tolerance
   * @throws Exception when reflection access fails
   */
  static void assertFloatingFieldEquals(
      Object target, String fieldName, double expected, double tolerance) throws Exception {
    Number value = (Number) getFieldValue(target, fieldName);
    assertEquals(expected, value.doubleValue(), tolerance, "Field mismatch: " + fieldName);
  }

  /**
   * Asserts one decoded string field value.
   *
   * @param target decoded object
   * @param fieldName string field name
   * @param expected expected string value
   * @throws Exception when reflection access fails
   */
  static void assertStringFieldEquals(Object target, String fieldName, String expected)
      throws Exception {
    assertEquals(expected, getFieldValue(target, fieldName), "Field mismatch: " + fieldName);
  }

  /**
   * Asserts one decoded byte-array field value.
   *
   * @param target decoded object
   * @param fieldName byte-array field name
   * @param expected expected byte-array value
   * @throws Exception when reflection access fails
   */
  static void assertByteArrayFieldEquals(Object target, String fieldName, byte[] expected)
      throws Exception {
    byte[] actual = (byte[]) getFieldValue(target, fieldName);
    assertArrayEquals(expected, actual, "Field mismatch: " + fieldName);
  }

  /**
   * Asserts one decoded numeric-array field value.
   *
   * @param target decoded object
   * @param fieldName numeric-array field name
   * @param expected expected numeric values
   * @throws Exception when reflection access fails
   */
  static void assertNumericArrayFieldEquals(Object target, String fieldName, long[] expected)
      throws Exception {
    Object actual = getFieldValue(target, fieldName);
    assertEquals(expected.length, Array.getLength(actual), "Length mismatch: " + fieldName);
    for (int index = 0; index < expected.length; index++) {
      Number number = (Number) Array.get(actual, index);
      assertEquals(
          expected[index], number.longValue(), "Value mismatch: " + fieldName + "[" + index + "]");
    }
  }

  /**
   * One process execution result.
   *
   * @param exitCode process exit code
   * @param output combined stdout and stderr text
   */
  record CommandResult(int exitCode, String output) {
    /** Creates one process result value. */
    CommandResult {}
  }
}
