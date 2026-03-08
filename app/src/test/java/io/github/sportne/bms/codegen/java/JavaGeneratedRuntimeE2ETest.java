package io.github.sportne.bms.codegen.java;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.BmsCompiler;
import io.github.sportne.bms.model.resolved.ResolvedSchema;
import io.github.sportne.bms.testutil.TestSupport;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end runtime tests for generated Java source. */
class JavaGeneratedRuntimeE2ETest {

  @TempDir Path tempDir;

  /** Contract: generated Java compiles and round-trips encode/decode for one all-supported spec. */
  @Test
  void generatedJavaCompilesAndRoundTripsForAllSupportedFixture() throws Exception {
    ResolvedSchema schema = compileFixture("specs/java-e2e-all-supported-valid.xml");
    JavaCodeGenerator generator = new JavaCodeGenerator();
    Path sourceDirectory = tempDir.resolve("generated-src");
    Path classDirectory = tempDir.resolve("generated-classes");

    generator.generate(schema, sourceDirectory);

    try (URLClassLoader classLoader = compileGeneratedSources(sourceDirectory, classDirectory)) {
      Class<?> messageClass = classLoader.loadClass("acme.telemetry.e2e.AllSupportedFrame");
      Object source = messageClass.getDeclaredConstructor().newInstance();

      setFieldValue(source, "version", (short) 2);
      setFieldValue(source, "count", (short) 2);
      setFieldValue(source, "nameLength", (short) 4);
      setFieldValue(source, "statusBits", (short) 3);
      setFieldValue(source, "ratio", 1.5d);
      setFieldValue(source, "temperature", 25.3d);
      setArrayField(source, "fixedValues", new long[] {1, 2});
      setArrayField(source, "samples", new long[] {257, 513});
      setFieldValue(source, "payload", new byte[] {10, 20, 30});
      setFieldValue(source, "tail", new byte[] {7, 8, 9});
      setFieldValue(source, "title", "BMS!");
      setFieldValue(source, "modeValue", (short) 4);
      setFieldValue(source, "nestedValue", 1025);
      setFieldValue(source, "alwaysValue", (short) 9);

      Method encodeMethod = messageClass.getMethod("encode");
      byte[] encoded = (byte[]) encodeMethod.invoke(source);
      assertTrue(encoded.length > 32, "Encoded bytes should contain payload and sha256 checksum.");

      Method decodeMethod = messageClass.getMethod("decode", byte[].class);
      Object decoded = decodeMethod.invoke(null, encoded);

      assertEquals((short) 2, readNumberField(decoded, "version").shortValue());
      assertEquals((short) 2, readNumberField(decoded, "count").shortValue());
      assertEquals((short) 4, readNumberField(decoded, "nameLength").shortValue());
      assertEquals((short) 3, readNumberField(decoded, "statusBits").shortValue());
      assertEquals(1.5d, readNumberField(decoded, "ratio").doubleValue(), 0.0001d);
      assertEquals(25.3d, readNumberField(decoded, "temperature").doubleValue(), 0.0001d);
      assertArrayFieldEquals(decoded, "fixedValues", new long[] {1, 2});
      assertArrayFieldEquals(decoded, "samples", new long[] {257, 513});
      assertArrayEquals(new byte[] {10, 20, 30}, (byte[]) readFieldValue(decoded, "payload"));
      assertArrayEquals(new byte[] {7, 8, 9}, (byte[]) readFieldValue(decoded, "tail"));
      assertEquals("BMS!", readFieldValue(decoded, "title"));
      assertEquals((short) 4, readNumberField(decoded, "modeValue").shortValue());
      assertEquals(1025, readNumberField(decoded, "nestedValue").intValue());
      assertEquals((short) 9, readNumberField(decoded, "alwaysValue").shortValue());
    }
  }

  /** Contract: generated decode rejects modified bytes when sha256 checksum no longer matches. */
  @Test
  void generatedJavaDecodeRejectsTamperedSha256Checksum() throws Exception {
    ResolvedSchema schema = compileFixture("specs/java-e2e-all-supported-valid.xml");
    JavaCodeGenerator generator = new JavaCodeGenerator();
    Path sourceDirectory = tempDir.resolve("generated-src-mismatch");
    Path classDirectory = tempDir.resolve("generated-classes-mismatch");

    generator.generate(schema, sourceDirectory);

    try (URLClassLoader classLoader = compileGeneratedSources(sourceDirectory, classDirectory)) {
      Class<?> messageClass = classLoader.loadClass("acme.telemetry.e2e.AllSupportedFrame");
      Object source = messageClass.getDeclaredConstructor().newInstance();

      setFieldValue(source, "version", (short) 2);
      setFieldValue(source, "count", (short) 1);
      setFieldValue(source, "nameLength", (short) 4);
      setFieldValue(source, "statusBits", (short) 1);
      setFieldValue(source, "ratio", 1.0d);
      setFieldValue(source, "temperature", 20.0d);
      setArrayField(source, "fixedValues", new long[] {1, 1});
      setArrayField(source, "samples", new long[] {1});
      setFieldValue(source, "payload", new byte[] {1, 2, 3});
      setFieldValue(source, "tail", new byte[] {4, 5});
      setFieldValue(source, "title", "ABCD");
      setFieldValue(source, "modeValue", (short) 1);
      setFieldValue(source, "nestedValue", 2);
      setFieldValue(source, "alwaysValue", (short) 3);

      Method encodeMethod = messageClass.getMethod("encode");
      byte[] encoded = (byte[]) encodeMethod.invoke(source);
      encoded[0] = (byte) (encoded[0] + 1);

      Method decodeMethod = messageClass.getMethod("decode", byte[].class);
      InvocationTargetException invocationTargetException =
          assertThrows(InvocationTargetException.class, () -> decodeMethod.invoke(null, encoded));
      assertTrue(invocationTargetException.getCause() instanceof IllegalArgumentException);
      assertTrue(
          invocationTargetException
              .getCause()
              .getMessage()
              .contains("Checksum mismatch for sha256 range 0..9."));
    }
  }

  /** Contract: generated decode rejects modified bytes when crc16 checksum no longer matches. */
  @Test
  void generatedJavaDecodeRejectsTamperedCrc16Checksum() throws Exception {
    assertTamperedChecksumRejected(
        "specs/checksum-crc16-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc16Frame",
        "crc16",
        "0..1");
  }

  /** Contract: generated decode rejects modified bytes when crc32 checksum no longer matches. */
  @Test
  void generatedJavaDecodeRejectsTamperedCrc32Checksum() throws Exception {
    assertTamperedChecksumRejected(
        "specs/checksum-crc32-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc32Frame",
        "crc32",
        "0..1");
  }

  /** Contract: generated decode rejects modified bytes when crc64 checksum no longer matches. */
  @Test
  void generatedJavaDecodeRejectsTamperedCrc64Checksum() throws Exception {
    assertTamperedChecksumRejected(
        "specs/checksum-crc64-valid.xml",
        "acme.telemetry.conditional.algorithms.ChecksumCrc64Frame",
        "crc64",
        "0..1");
  }

  /**
   * Contract: when a compound `if` condition is false, conditional members are skipped in
   * encode/decode.
   */
  @Test
  void generatedJavaSkipsCompoundConditionalMembersWhenConditionIsFalse() throws Exception {
    ResolvedSchema schema = compileFixture("specs/java-e2e-all-supported-valid.xml");
    JavaCodeGenerator generator = new JavaCodeGenerator();
    Path sourceDirectory = tempDir.resolve("generated-src-condition-false");
    Path classDirectory = tempDir.resolve("generated-classes-condition-false");

    generator.generate(schema, sourceDirectory);

    try (URLClassLoader classLoader = compileGeneratedSources(sourceDirectory, classDirectory)) {
      Class<?> messageClass = classLoader.loadClass("acme.telemetry.e2e.AllSupportedFrame");
      Object source = messageClass.getDeclaredConstructor().newInstance();

      setFieldValue(source, "version", (short) 1);
      setFieldValue(source, "count", (short) 1);
      setFieldValue(source, "nameLength", (short) 4);
      setFieldValue(source, "statusBits", (short) 1);
      setFieldValue(source, "ratio", 1.0d);
      setFieldValue(source, "temperature", 20.0d);
      setArrayField(source, "fixedValues", new long[] {1, 1});
      setArrayField(source, "samples", new long[] {1});
      setFieldValue(source, "payload", new byte[] {1, 2, 3});
      setFieldValue(source, "tail", new byte[] {4, 5});
      setFieldValue(source, "title", "ABCD");
      setFieldValue(source, "alwaysValue", (short) 3);

      Method encodeMethod = messageClass.getMethod("encode");
      byte[] encoded = (byte[]) encodeMethod.invoke(source);

      Method decodeMethod = messageClass.getMethod("decode", byte[].class);
      Object decoded = decodeMethod.invoke(null, encoded);
      assertEquals((short) 1, readNumberField(decoded, "version").shortValue());
      assertEquals((short) 0, readNumberField(decoded, "modeValue").shortValue());
      assertEquals(0, readNumberField(decoded, "nestedValue").intValue());
      assertEquals((short) 3, readNumberField(decoded, "alwaysValue").shortValue());
    }
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

  /**
   * Generates, compiles, and executes one fixture to verify tampered checksum detection.
   *
   * @param fixtureResource classpath resource path to the XML fixture
   * @param generatedClassName fully qualified generated Java message class name
   * @param algorithmName checksum algorithm expected in the error message
   * @param rangeText checksum range expected in the error message
   * @throws Exception if generation or runtime invocation fails unexpectedly
   */
  private void assertTamperedChecksumRejected(
      String fixtureResource, String generatedClassName, String algorithmName, String rangeText)
      throws Exception {
    ResolvedSchema schema = compileFixture(fixtureResource);
    JavaCodeGenerator generator = new JavaCodeGenerator();
    String fixtureStem =
        fixtureResource
            .replace("specs/", "")
            .replace(".xml", "")
            .replace('-', '_')
            .replace('/', '_');
    Path sourceDirectory = tempDir.resolve("generated-src-" + fixtureStem);
    Path classDirectory = tempDir.resolve("generated-classes-" + fixtureStem);

    generator.generate(schema, sourceDirectory);

    try (URLClassLoader classLoader = compileGeneratedSources(sourceDirectory, classDirectory)) {
      Class<?> messageClass = classLoader.loadClass(generatedClassName);
      Object source = messageClass.getDeclaredConstructor().newInstance();
      setFieldValue(source, "version", (short) 2);
      setFieldValue(source, "payload", (short) 7);

      Method encodeMethod = messageClass.getMethod("encode");
      byte[] encoded = (byte[]) encodeMethod.invoke(source);
      encoded[0] = (byte) (encoded[0] + 1);

      Method decodeMethod = messageClass.getMethod("decode", byte[].class);
      InvocationTargetException invocationTargetException =
          assertThrows(InvocationTargetException.class, () -> decodeMethod.invoke(null, encoded));
      assertTrue(invocationTargetException.getCause() instanceof IllegalArgumentException);
      assertTrue(
          invocationTargetException
              .getCause()
              .getMessage()
              .contains("Checksum mismatch for " + algorithmName + " range " + rangeText + "."));
    }
  }

  /**
   * Compiles generated Java files and returns a class loader for the compiled output.
   *
   * @param sourceDirectory root directory that contains generated Java source files
   * @param classDirectory destination directory for compiled `.class` files
   * @return class loader that can load the compiled generated classes
   * @throws Exception if Java compilation infrastructure fails unexpectedly
   */
  private static URLClassLoader compileGeneratedSources(Path sourceDirectory, Path classDirectory)
      throws Exception {
    List<Path> javaSources = collectJavaSources(sourceDirectory);
    assertTrue(!javaSources.isEmpty(), "Expected generated Java sources to compile.");
    Files.createDirectories(classDirectory);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(
        compiler != null, "System JavaCompiler is required to run runtime generation tests.");

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
    return new URLClassLoader(new URL[] {classDirectoryUrl});
  }

  /**
   * Collects generated Java source files under one directory.
   *
   * @param sourceDirectory root directory to scan
   * @return deterministic list of Java source file paths
   * @throws Exception if directory traversal fails
   */
  private static List<Path> collectJavaSources(Path sourceDirectory) throws Exception {
    List<Path> javaSources = new ArrayList<>();
    try (var sourceStream = Files.walk(sourceDirectory)) {
      sourceStream
          .filter(
              path -> {
                Path fileName = path.getFileName();
                return fileName != null && fileName.toString().endsWith(".java");
              })
          .sorted()
          .forEach(javaSources::add);
    }
    return javaSources;
  }

  /**
   * Sets one public field on a generated message instance.
   *
   * @param target generated message object
   * @param fieldName field name to set
   * @param value value to assign
   * @throws Exception if reflection access fails
   */
  private static void setFieldValue(Object target, String fieldName, Object value)
      throws Exception {
    Field field = target.getClass().getField(fieldName);
    field.set(target, value);
  }

  /**
   * Sets one numeric array field on a generated message instance.
   *
   * @param target generated message object
   * @param fieldName array field name to set
   * @param values numeric values that should be copied into the array
   * @throws Exception if reflection access fails
   */
  private static void setArrayField(Object target, String fieldName, long[] values)
      throws Exception {
    Field field = target.getClass().getField(fieldName);
    Class<?> componentType = field.getType().getComponentType();
    Object array = Array.newInstance(componentType, values.length);
    for (int index = 0; index < values.length; index++) {
      setArrayValue(array, componentType, index, values[index]);
    }
    field.set(target, array);
  }

  /**
   * Sets one numeric value inside a primitive array created through reflection.
   *
   * @param array destination primitive array
   * @param componentType primitive component type
   * @param index target index
   * @param value numeric value to assign
   */
  private static void setArrayValue(Object array, Class<?> componentType, int index, long value) {
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
    throw new IllegalArgumentException(
        "Unsupported numeric array component type: " + componentType);
  }

  /**
   * Reads one public field value from a generated message instance.
   *
   * @param target generated message object
   * @param fieldName field name to read
   * @return field value
   * @throws Exception if reflection access fails
   */
  private static Object readFieldValue(Object target, String fieldName) throws Exception {
    Field field = target.getClass().getField(fieldName);
    return field.get(target);
  }

  /**
   * Reads one numeric public field from a generated message instance.
   *
   * @param target generated message object
   * @param fieldName field name to read
   * @return numeric field value
   * @throws Exception if reflection access fails
   */
  private static Number readNumberField(Object target, String fieldName) throws Exception {
    return (Number) readFieldValue(target, fieldName);
  }

  /**
   * Compares one numeric array field with expected values.
   *
   * @param target generated message object
   * @param fieldName array field name to compare
   * @param expected expected numeric values
   * @throws Exception if reflection access fails
   */
  private static void assertArrayFieldEquals(Object target, String fieldName, long[] expected)
      throws Exception {
    Object actual = readFieldValue(target, fieldName);
    assertEquals(expected.length, Array.getLength(actual));
    for (int index = 0; index < expected.length; index++) {
      Number numericValue = (Number) Array.get(actual, index);
      assertEquals(expected[index], numericValue.longValue());
    }
  }
}
