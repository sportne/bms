package io.github.sportne.bms.model;

/**
 * Logical operators allowed between comparisons in text and structured {@code if} conditions.
 *
 * <p>The schema uses lower-case XML tokens ({@code and}, {@code or}). Generated Java code uses
 * short-circuit symbols ({@code &&}, {@code ||}).
 */
public enum IfLogicalOperator {
  /** Logical conjunction. */
  AND("and", "&&"),
  /** Logical disjunction. */
  OR("or", "||");

  private final String xmlToken;
  private final String javaSymbol;

  /**
   * Creates one logical-operator definition.
   *
   * @param xmlToken XML token used in specs
   * @param javaSymbol Java operator symbol used by generated code
   */
  IfLogicalOperator(String xmlToken, String javaSymbol) {
    this.xmlToken = xmlToken;
    this.javaSymbol = javaSymbol;
  }

  /**
   * Returns the XML token for this operator.
   *
   * @return XML token string
   */
  public String xmlToken() {
    return xmlToken;
  }

  /**
   * Returns the Java operator symbol for this operator.
   *
   * @return Java symbol string
   */
  public String javaSymbol() {
    return javaSymbol;
  }

  /**
   * Parses one XML token into a logical operator.
   *
   * @param xmlToken XML token from a spec
   * @return parsed logical operator
   * @throws IllegalArgumentException when the token is unknown
   */
  public static IfLogicalOperator fromXmlToken(String xmlToken) {
    for (IfLogicalOperator operator : values()) {
      if (operator.xmlToken.equals(xmlToken)) {
        return operator;
      }
    }
    throw new IllegalArgumentException("Unknown if logical operator token: " + xmlToken);
  }
}
