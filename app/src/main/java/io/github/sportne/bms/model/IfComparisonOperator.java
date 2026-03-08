package io.github.sportne.bms.model;

/**
 * Comparison operators allowed in BMS {@code if} conditions.
 *
 * <p>Each operator has two text forms:
 *
 * <ul>
 *   <li>XML enum token used by structured conditions ({@code eq}, {@code ne}, and so on)
 *   <li>symbol used by legacy text conditions ({@code ==}, {@code !=}, and so on)
 * </ul>
 */
public enum IfComparisonOperator {
  /** Equality operator. */
  EQ("eq", "=="),
  /** Inequality operator. */
  NE("ne", "!="),
  /** Less-than operator. */
  LT("lt", "<"),
  /** Less-than-or-equal operator. */
  LTE("lte", "<="),
  /** Greater-than operator. */
  GT("gt", ">"),
  /** Greater-than-or-equal operator. */
  GTE("gte", ">=");

  private final String xmlToken;
  private final String symbol;

  /**
   * Creates one operator definition.
   *
   * @param xmlToken XML token used in structured {@code if} attributes
   * @param symbol legacy text symbol
   */
  IfComparisonOperator(String xmlToken, String symbol) {
    this.xmlToken = xmlToken;
    this.symbol = symbol;
  }

  /**
   * Returns the XML token for this operator.
   *
   * @return XML token string (for example {@code eq})
   */
  public String xmlToken() {
    return xmlToken;
  }

  /**
   * Returns the symbol form for this operator.
   *
   * @return symbol string (for example {@code ==})
   */
  public String symbol() {
    return symbol;
  }

  /**
   * Parses one XML token into an operator.
   *
   * @param xmlToken XML token from {@code if@operator}
   * @return parsed operator
   * @throws IllegalArgumentException when the token is unknown
   */
  public static IfComparisonOperator fromXmlToken(String xmlToken) {
    for (IfComparisonOperator operator : values()) {
      if (operator.xmlToken.equals(xmlToken)) {
        return operator;
      }
    }
    throw new IllegalArgumentException("Unknown if comparison operator token: " + xmlToken);
  }

  /**
   * Parses one symbol into an operator.
   *
   * @param symbol symbol from one text condition comparison
   * @return parsed operator
   * @throws IllegalArgumentException when the symbol is unknown
   */
  public static IfComparisonOperator fromSymbol(String symbol) {
    for (IfComparisonOperator operator : values()) {
      if (operator.symbol.equals(symbol)) {
        return operator;
      }
    }
    throw new IllegalArgumentException("Unknown if comparison operator symbol: " + symbol);
  }
}
