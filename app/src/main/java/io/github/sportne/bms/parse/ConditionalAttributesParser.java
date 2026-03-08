package io.github.sportne.bms.parse;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import io.github.sportne.bms.util.BmsException;
import java.nio.file.Path;
import javax.xml.stream.XMLStreamReader;

/** Parses and canonicalizes condition attributes on {@code if} elements. */
final class ConditionalAttributesParser {

  /** Creates one condition-attribute parser. */
  ConditionalAttributesParser() {}

  /**
   * Validates and canonicalizes one parsed {@code <if>} condition.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @return canonical {@code if@test} text consumed by semantic parsing
   * @throws BmsException if attributes are missing or conflicting
   */
  String normalizeIfConditionText(Path specPath, XMLStreamReader reader) throws BmsException {
    IfAttributes ifAttributes = readIfAttributes(reader);
    if (ifAttributes.test() != null) {
      validateNoStructuredIfAttributes(specPath, reader, ifAttributes);
      return ifAttributes.test();
    }
    return normalizeStructuredIfConditionText(specPath, reader, ifAttributes);
  }

  /**
   * Reads all condition-related attributes from one {@code <if>} element.
   *
   * @param reader active XML reader positioned on {@code <if>}
   * @return immutable attribute bundle
   */
  private static IfAttributes readIfAttributes(XMLStreamReader reader) {
    return new IfAttributes(
        reader.getAttributeValue(null, "test"),
        reader.getAttributeValue(null, "field"),
        reader.getAttributeValue(null, "operator"),
        reader.getAttributeValue(null, "value"),
        reader.getAttributeValue(null, "logicalOperator"),
        reader.getAttributeValue(null, "field2"),
        reader.getAttributeValue(null, "operator2"),
        reader.getAttributeValue(null, "value2"));
  }

  /**
   * Validates that text-style conditions are not mixed with structured attributes.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if structured attributes are present
   */
  private void validateNoStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.hasAnyStructuredAttributes()) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "<if> must use either test=\"...\" or structured comparison attributes, not both.");
    }
  }

  /**
   * Validates and canonicalizes one structured if condition (simple or compound).
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @return canonical condition text
   * @throws BmsException if required structured attributes are missing
   */
  private String normalizeStructuredIfConditionText(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    validatePrimaryStructuredIfAttributes(specPath, reader, ifAttributes);
    String firstComparison =
        ifAttributes.field()
            + " "
            + parseIfComparisonOperatorSymbol(specPath, reader, ifAttributes.operator())
            + " "
            + ifAttributes.value();
    if (!ifAttributes.hasCompoundStructuredAttributes()) {
      return firstComparison;
    }
    validateCompoundStructuredIfAttributes(specPath, reader, ifAttributes);
    return firstComparison
        + " "
        + parseIfLogicalOperatorToken(specPath, reader, ifAttributes.logicalOperator())
        + " "
        + ifAttributes.field2()
        + " "
        + parseIfComparisonOperatorSymbol(specPath, reader, ifAttributes.operator2())
        + " "
        + ifAttributes.value2();
  }

  /**
   * Validates required fields for a primary structured comparison.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if any required primary attribute is missing
   */
  private void validatePrimaryStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.field() == null
        || ifAttributes.operator() == null
        || ifAttributes.value() == null) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_MISSING_ATTRIBUTE",
          "<if> requires either test=\"...\" or structured comparison attributes.");
    }
  }

  /**
   * Validates required fields for a compound structured comparison.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param ifAttributes parsed attribute bundle
   * @throws BmsException if any required compound attribute is missing
   */
  private void validateCompoundStructuredIfAttributes(
      Path specPath, XMLStreamReader reader, IfAttributes ifAttributes) throws BmsException {
    if (ifAttributes.logicalOperator() == null
        || ifAttributes.field2() == null
        || ifAttributes.operator2() == null
        || ifAttributes.value2() == null) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_MISSING_ATTRIBUTE",
          "<if> compound structured comparisons require logicalOperator, field2, operator2, and value2.");
    }
  }

  /**
   * Converts one enum-style if-comparison operator to canonical symbol text.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param operatorValue enum operator literal from XML
   * @return canonical comparison symbol used by downstream stages
   * @throws BmsException if the operator value is unsupported
   */
  private String parseIfComparisonOperatorSymbol(
      Path specPath, XMLStreamReader reader, String operatorValue) throws BmsException {
    try {
      return IfComparisonOperator.fromXmlToken(operatorValue).symbol();
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported if comparison operator value: " + operatorValue);
    }
  }

  /**
   * Converts one enum-style logical operator to canonical text used in {@code if@test}.
   *
   * @param specPath source file path used in diagnostics
   * @param reader active XML reader positioned on {@code <if>}
   * @param operatorValue enum logical operator literal from XML
   * @return canonical logical text used by semantic parsing
   * @throws BmsException if the logical-operator value is unsupported
   */
  private String parseIfLogicalOperatorToken(
      Path specPath, XMLStreamReader reader, String operatorValue) throws BmsException {
    try {
      return IfLogicalOperator.fromXmlToken(operatorValue).xmlToken();
    } catch (IllegalArgumentException exception) {
      throw ParserSupport.parserError(
          specPath,
          reader,
          "PARSER_INVALID_ATTRIBUTE",
          "Unsupported if logical operator value: " + operatorValue);
    }
  }

  /**
   * Parsed attribute bundle for one {@code <if>} condition declaration.
   *
   * @param test text condition value
   * @param field first comparison field name
   * @param operator first comparison operator token
   * @param value first comparison literal
   * @param logicalOperator logical operator token between comparisons
   * @param field2 second comparison field name
   * @param operator2 second comparison operator token
   * @param value2 second comparison literal
   */
  private record IfAttributes(
      String test,
      String field,
      String operator,
      String value,
      String logicalOperator,
      String field2,
      String operator2,
      String value2) {

    /**
     * Returns whether any structured condition attribute is present.
     *
     * @return {@code true} when any structured attribute is present
     */
    private boolean hasAnyStructuredAttributes() {
      return field != null
          || operator != null
          || value != null
          || logicalOperator != null
          || field2 != null
          || operator2 != null
          || value2 != null;
    }

    /**
     * Returns whether any compound-structured condition attribute is present.
     *
     * @return {@code true} when any compound attribute is present
     */
    private boolean hasCompoundStructuredAttributes() {
      return logicalOperator != null || field2 != null || operator2 != null || value2 != null;
    }
  }
}
