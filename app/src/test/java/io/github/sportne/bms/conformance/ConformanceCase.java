package io.github.sportne.bms.conformance;

/**
 * One cross-language conformance case definition.
 *
 * <p>Each case points to one XML fixture and includes canonical setup/assertion logic for both
 * generated Java and generated C++ code.
 *
 * @param id stable case identifier used in test names and artifact paths
 * @param fixtureResource classpath resource path to the XML fixture
 * @param javaClassName fully qualified generated Java message class name
 * @param cppIncludePath generated C++ header include path
 * @param cppQualifiedTypeName fully qualified generated C++ message type name
 * @param javaConfigurer canonical Java source-object setup callback
 * @param javaAsserter Java decoded-object assertion callback
 * @param cppSourceSetupSnippet C++ source-object setup snippet
 * @param cppDecodedAssertionSnippet C++ decoded-object assertion snippet
 */
public record ConformanceCase(
    String id,
    String fixtureResource,
    String javaClassName,
    String cppIncludePath,
    String cppQualifiedTypeName,
    JavaConfigurer javaConfigurer,
    JavaAsserter javaAsserter,
    String cppSourceSetupSnippet,
    String cppDecodedAssertionSnippet) {

  /**
   * Functional contract for Java source-object setup.
   *
   * @param target generated Java message instance to configure
   * @param classLoader class loader that loaded generated message classes
   * @throws Exception when reflection setup fails
   */
  @FunctionalInterface
  interface JavaConfigurer {
    /**
     * Applies canonical source-object values.
     *
     * @param target generated Java message instance to configure
     * @param classLoader class loader for generated classes
     * @throws Exception when reflection setup fails
     */
    void configure(Object target, ClassLoader classLoader) throws Exception;
  }

  /**
   * Functional contract for Java decoded-object assertions.
   *
   * @param decoded generated Java decoded instance
   * @param classLoader class loader that loaded generated message classes
   * @throws Exception when reflection assertions fail
   */
  @FunctionalInterface
  interface JavaAsserter {
    /**
     * Validates canonical decoded values.
     *
     * @param decoded generated Java decoded instance
     * @param classLoader class loader for generated classes
     * @throws Exception when reflection assertions fail
     */
    void assertDecoded(Object decoded, ClassLoader classLoader) throws Exception;
  }
}
