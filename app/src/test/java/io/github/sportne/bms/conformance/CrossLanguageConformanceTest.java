package io.github.sportne.bms.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import io.github.sportne.bms.model.resolved.ResolvedSchema;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Cross-language conformance tests for generated Java and C++ output. */
class CrossLanguageConformanceTest {

  /**
   * Verifies one full conformance case from generation to cross-language decode checks.
   *
   * @param conformanceCase conformance case definition
   * @throws Exception when generation, compilation, or runtime checks fail
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("conformanceCases")
  void generatedJavaAndCppStayByteCompatible(ConformanceCase conformanceCase) throws Exception {
    Path caseDirectory = ConformanceRuntimeSupport.prepareCaseDirectory(conformanceCase.id());
    Path javaOutputDirectory = caseDirectory.resolve("generated-java");
    Path cppOutputDirectory = caseDirectory.resolve("generated-cpp");
    Path javaClassDirectory = caseDirectory.resolve("generated-java-classes");

    ResolvedSchema schema =
        ConformanceRuntimeSupport.compileFixture(conformanceCase.fixtureResource());
    ConformanceRuntimeSupport.generateBothTargets(schema, javaOutputDirectory, cppOutputDirectory);

    try (URLClassLoader classLoader =
        ConformanceRuntimeSupport.compileGeneratedJavaSources(
            javaOutputDirectory, javaClassDirectory)) {
      Class<?> messageClass = classLoader.loadClass(conformanceCase.javaClassName());
      Object source = messageClass.getDeclaredConstructor().newInstance();
      conformanceCase.javaConfigurer().configure(source, classLoader);

      Method encodeMethod = messageClass.getMethod("encode");
      byte[] javaBytes = (byte[]) encodeMethod.invoke(source);

      Path harnessExecutable =
          ConformanceRuntimeSupport.compileCppHarness(
              caseDirectory,
              cppOutputDirectory,
              cppHarnessSource(conformanceCase),
              conformanceCase.id() + "_harness");
      byte[] cppBytes = ConformanceRuntimeSupport.runCppEncode(harnessExecutable, caseDirectory);

      assertArrayEquals(
          javaBytes,
          cppBytes,
          "Java and C++ bytes differ for fixture " + conformanceCase.fixtureResource());

      ConformanceRuntimeSupport.runCppDecodeAssert(harnessExecutable, caseDirectory, javaBytes);

      Method decodeMethod = messageClass.getMethod("decode", byte[].class);
      Object decoded = decodeMethod.invoke(null, (Object) cppBytes);
      conformanceCase.javaAsserter().assertDecoded(decoded, classLoader);
    }
  }

  /**
   * Supplies parameterized conformance cases.
   *
   * @return stream of conformance cases
   */
  private static Stream<ConformanceCase> conformanceCases() {
    return ConformanceCaseCatalog.cases().stream();
  }

  /**
   * Builds one deterministic C++ harness source file for the given case.
   *
   * @param conformanceCase conformance case definition
   * @return C++ harness source text
   */
  private static String cppHarnessSource(ConformanceCase conformanceCase) {
    String template =
        """
        #include "__INCLUDE__"

        #include <algorithm>
        #include <cctype>
        #include <cmath>
        #include <cstdint>
        #include <iostream>
        #include <stdexcept>
        #include <string>
        #include <vector>

        static std::string bytesToHex(const std::vector<std::uint8_t>& bytes) {
          static constexpr char HEX[] = "0123456789abcdef";
          std::string result;
          result.reserve(bytes.size() * 2U);
          for (std::uint8_t value : bytes) {
            result.push_back(HEX[(value >> 4) & 0x0FU]);
            result.push_back(HEX[value & 0x0FU]);
          }
          return result;
        }

        static std::vector<std::uint8_t> hexToBytes(const std::string& hex) {
          if ((hex.size() % 2U) != 0U) {
            throw std::invalid_argument("Hex input must contain an even number of characters.");
          }
          std::vector<std::uint8_t> bytes;
          bytes.reserve(hex.size() / 2U);
          auto hexValue = [](char value) -> int {
            if (value >= '0' && value <= '9') {
              return value - '0';
            }
            char lower = static_cast<char>(std::tolower(static_cast<unsigned char>(value)));
            if (lower >= 'a' && lower <= 'f') {
              return 10 + (lower - 'a');
            }
            return -1;
          };
          for (std::size_t index = 0; index < hex.size(); index += 2U) {
            int high = hexValue(hex[index]);
            int low = hexValue(hex[index + 1U]);
            if (high < 0 || low < 0) {
              throw std::invalid_argument("Hex input contains invalid characters.");
            }
            bytes.push_back(static_cast<std::uint8_t>((high << 4) | low));
          }
          return bytes;
        }

        static int runEncode() {
          using MessageType = __CPP_TYPE__;
          MessageType source{};
        __SOURCE_SETUP__
          std::vector<std::uint8_t> encoded = source.encode();
          std::cout << bytesToHex(encoded);
          return 0;
        }

        static int runDecodeAssert(const std::vector<std::uint8_t>& bytes) {
          using MessageType = __CPP_TYPE__;
          MessageType decoded = MessageType::decode(bytes);
        __DECODE_ASSERT__
          return 0;
        }

        int main(int argc, char** argv) {
          if (argc < 2) {
            return 2;
          }
          std::string mode = argv[1];
          if (mode == "encode") {
            return runEncode();
          }
          if (mode == "decodeAssert") {
            if (argc < 3) {
              return 3;
            }
            std::vector<std::uint8_t> bytes = hexToBytes(argv[2]);
            return runDecodeAssert(bytes);
          }
          return 4;
        }
        """;
    return template
        .replace("__INCLUDE__", conformanceCase.cppIncludePath())
        .replace("__CPP_TYPE__", conformanceCase.cppQualifiedTypeName())
        .replace("__SOURCE_SETUP__", indentSnippet(conformanceCase.cppSourceSetupSnippet()))
        .replace("__DECODE_ASSERT__", indentSnippet(conformanceCase.cppDecodedAssertionSnippet()));
  }

  /**
   * Indents one multiline snippet by two spaces for harness embedding.
   *
   * @param snippet raw snippet text
   * @return indented snippet text
   */
  private static String indentSnippet(String snippet) {
    return snippet
        .lines()
        .map(line -> "  " + line)
        .reduce("", (left, right) -> left + right + "\n");
  }
}
