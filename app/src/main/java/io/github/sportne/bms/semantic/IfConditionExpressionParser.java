package io.github.sportne.bms.semantic;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Parses textual {@code if} condition expressions into a small syntax tree.
 *
 * <p>Supported syntax:
 *
 * <ul>
 *   <li>Comparison: {@code field == literal}, {@code field != literal}, {@code field < literal},
 *       {@code field <= literal}, {@code field > literal}, {@code field >= literal}
 *   <li>Logical operators: {@code and}, {@code or}
 *   <li>Grouping: parentheses
 * </ul>
 *
 * <p>Legacy tokens {@code &&} and {@code ||} are intentionally rejected.
 */
final class IfConditionExpressionParser {
  private final List<Token> tokens;
  private int tokenIndex;

  /**
   * Creates one parser instance.
   *
   * @param tokens pre-tokenized expression stream
   */
  private IfConditionExpressionParser(List<Token> tokens) {
    this.tokens = tokens;
    tokenIndex = 0;
  }

  /**
   * Parses one expression string.
   *
   * @param expression raw expression text from XML
   * @return parsed condition tree
   * @throws IfConditionParseException when syntax is invalid
   */
  static ParsedCondition parse(String expression) throws IfConditionParseException {
    List<Token> tokens = tokenize(expression);
    IfConditionExpressionParser parser = new IfConditionExpressionParser(tokens);
    ParsedCondition parsedCondition = parser.parseOrExpression();
    parser.expect(TokenType.END, "Unexpected trailing tokens in if condition.");
    return parsedCondition;
  }

  /**
   * Parses one {@code or}-precedence expression.
   *
   * @return parsed condition subtree
   * @throws IfConditionParseException when syntax is invalid
   */
  private ParsedCondition parseOrExpression() throws IfConditionParseException {
    ParsedCondition left = parseAndExpression();
    while (match(TokenType.OR)) {
      ParsedCondition right = parseAndExpression();
      left = new ParsedLogicalCondition(left, IfLogicalOperator.OR, right);
    }
    return left;
  }

  /**
   * Parses one {@code and}-precedence expression.
   *
   * @return parsed condition subtree
   * @throws IfConditionParseException when syntax is invalid
   */
  private ParsedCondition parseAndExpression() throws IfConditionParseException {
    ParsedCondition left = parsePrimaryExpression();
    while (match(TokenType.AND)) {
      ParsedCondition right = parsePrimaryExpression();
      left = new ParsedLogicalCondition(left, IfLogicalOperator.AND, right);
    }
    return left;
  }

  /**
   * Parses one primary expression.
   *
   * @return parsed condition subtree
   * @throws IfConditionParseException when syntax is invalid
   */
  private ParsedCondition parsePrimaryExpression() throws IfConditionParseException {
    if (match(TokenType.LEFT_PAREN)) {
      ParsedCondition grouped = parseOrExpression();
      expect(TokenType.RIGHT_PAREN, "Missing ')' in if condition.");
      return grouped;
    }
    return parseComparisonExpression();
  }

  /**
   * Parses one comparison expression.
   *
   * @return parsed comparison node
   * @throws IfConditionParseException when syntax is invalid
   */
  private ParsedCondition parseComparisonExpression() throws IfConditionParseException {
    Token fieldToken = expect(TokenType.ATOM, "Expected field name in if condition.");
    if (!isIdentifier(fieldToken.text())) {
      throw error("Field name in if condition is not a valid identifier: " + fieldToken.text());
    }

    Token operatorToken =
        expect(TokenType.COMPARISON_OPERATOR, "Expected comparison operator in if condition.");
    IfComparisonOperator operator;
    try {
      operator = IfComparisonOperator.fromSymbol(operatorToken.text());
    } catch (IllegalArgumentException exception) {
      throw error("Unsupported comparison operator in if condition: " + operatorToken.text());
    }

    Token literalToken = expect(TokenType.ATOM, "Expected literal value in if condition.");
    BigInteger literal;
    try {
      literal = parseNumericLiteral(literalToken.text());
    } catch (NumberFormatException exception) {
      throw error("Literal value in if condition is not numeric: " + literalToken.text());
    }

    return new ParsedComparisonCondition(fieldToken.text(), operator, literal);
  }

  /**
   * Returns whether the current token matches one expected type.
   *
   * @param expectedType token type to match
   * @return {@code true} when matched and consumed
   */
  private boolean match(TokenType expectedType) {
    if (peek().type() != expectedType) {
      return false;
    }
    tokenIndex++;
    return true;
  }

  /**
   * Consumes one token of the expected type.
   *
   * @param expectedType token type to consume
   * @param message message shown when token does not match
   * @return consumed token
   * @throws IfConditionParseException when token does not match
   */
  private Token expect(TokenType expectedType, String message) throws IfConditionParseException {
    Token token = peek();
    if (token.type() != expectedType) {
      throw error(message);
    }
    tokenIndex++;
    return token;
  }

  /**
   * Returns the current token without consuming it.
   *
   * @return current token
   */
  private Token peek() {
    return tokens.get(tokenIndex);
  }

  /**
   * Builds one parse exception.
   *
   * @param message human-readable error message
   * @return parse exception instance
   */
  private IfConditionParseException error(String message) {
    return new IfConditionParseException(message);
  }

  /**
   * Tokenizes one raw condition expression.
   *
   * @param expression raw expression text
   * @return token stream that ends with {@link TokenType#END}
   * @throws IfConditionParseException when unsupported tokens are found
   */
  private static List<Token> tokenize(String expression) throws IfConditionParseException {
    String source = Objects.requireNonNull(expression, "expression");
    List<Token> tokens = new ArrayList<>();
    int index = 0;

    while (index < source.length()) {
      char current = source.charAt(index);
      if (Character.isWhitespace(current)) {
        index++;
        continue;
      }

      if (current == '(') {
        tokens.add(new Token(TokenType.LEFT_PAREN, "(", index));
        index++;
        continue;
      }
      if (current == ')') {
        tokens.add(new Token(TokenType.RIGHT_PAREN, ")", index));
        index++;
        continue;
      }

      if (startsWith(source, index, "&&") || startsWith(source, index, "||")) {
        throw new IfConditionParseException(
            "Legacy operators && and || are not supported. Use 'and' and 'or' instead.");
      }

      String comparator = readComparator(source, index);
      if (comparator != null) {
        tokens.add(new Token(TokenType.COMPARISON_OPERATOR, comparator, index));
        index += comparator.length();
        continue;
      }

      int start = index;
      while (index < source.length() && isAtomCharacter(source, index)) {
        index++;
      }
      if (start == index) {
        throw new IfConditionParseException(
            "Unsupported character in if condition at position " + index + ".");
      }

      String atom = source.substring(start, index);
      String lower = atom.toLowerCase(Locale.ROOT);
      if ("and".equals(lower)) {
        tokens.add(new Token(TokenType.AND, atom, start));
      } else if ("or".equals(lower)) {
        tokens.add(new Token(TokenType.OR, atom, start));
      } else {
        tokens.add(new Token(TokenType.ATOM, atom, start));
      }
    }

    tokens.add(new Token(TokenType.END, "", source.length()));
    return tokens;
  }

  /**
   * Returns whether one position starts with one expected token text.
   *
   * @param source full source expression
   * @param index start index
   * @param tokenText token text to match
   * @return {@code true} when token text matches
   */
  private static boolean startsWith(String source, int index, String tokenText) {
    return source.regionMatches(index, tokenText, 0, tokenText.length());
  }

  /**
   * Reads one comparison-operator token from a given source position.
   *
   * @param source full source expression
   * @param index start index
   * @return operator text, or {@code null} when none is present
   */
  private static String readComparator(String source, int index) {
    if (startsWith(source, index, "==")) {
      return "==";
    }
    if (startsWith(source, index, "!=")) {
      return "!=";
    }
    if (startsWith(source, index, "<=")) {
      return "<=";
    }
    if (startsWith(source, index, ">=")) {
      return ">=";
    }
    if (startsWith(source, index, "<")) {
      return "<";
    }
    if (startsWith(source, index, ">")) {
      return ">";
    }
    return null;
  }

  /**
   * Returns whether one character can appear in an atom token.
   *
   * @param source full source expression
   * @param index current index
   * @return {@code true} when the character belongs to an atom token
   */
  private static boolean isAtomCharacter(String source, int index) {
    char current = source.charAt(index);
    if (Character.isWhitespace(current)) {
      return false;
    }
    if (current == '(' || current == ')') {
      return false;
    }
    return readComparator(source, index) == null;
  }

  /**
   * Parses one numeric literal.
   *
   * @param literal raw literal text
   * @return parsed numeric value
   * @throws NumberFormatException when literal is not numeric
   */
  private static BigInteger parseNumericLiteral(String literal) {
    String trimmed = literal.trim();
    if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
      return new BigInteger(trimmed.substring(2), 16);
    }
    if (trimmed.startsWith("-") && (trimmed.startsWith("-0x") || trimmed.startsWith("-0X"))) {
      return new BigInteger(trimmed.substring(3), 16).negate();
    }
    if (trimmed.matches("-?[0-9]+")) {
      return new BigInteger(trimmed, 10);
    }
    return new BigInteger(trimmed, 16);
  }

  /**
   * Returns whether one string is a valid identifier.
   *
   * @param value candidate identifier
   * @return {@code true} when the string is a valid identifier
   */
  private static boolean isIdentifier(String value) {
    return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
  }

  /**
   * Parsed condition tree.
   *
   * <p>This is an intermediate semantic-only representation.
   */
  sealed interface ParsedCondition permits ParsedComparisonCondition, ParsedLogicalCondition {}

  /**
   * Parsed comparison node.
   *
   * @param fieldName referenced field name
   * @param operator parsed comparison operator
   * @param literal parsed numeric literal
   */
  record ParsedComparisonCondition(
      String fieldName, IfComparisonOperator operator, BigInteger literal)
      implements ParsedCondition {

    /** Creates one parsed comparison node. */
    ParsedComparisonCondition {
      fieldName = Objects.requireNonNull(fieldName, "fieldName");
      operator = Objects.requireNonNull(operator, "operator");
      literal = Objects.requireNonNull(literal, "literal");
    }
  }

  /**
   * Parsed logical binary node.
   *
   * @param left left child
   * @param operator logical operator between children
   * @param right right child
   */
  record ParsedLogicalCondition(
      ParsedCondition left, IfLogicalOperator operator, ParsedCondition right)
      implements ParsedCondition {

    /** Creates one parsed logical-condition node. */
    ParsedLogicalCondition {
      left = Objects.requireNonNull(left, "left");
      operator = Objects.requireNonNull(operator, "operator");
      right = Objects.requireNonNull(right, "right");
    }
  }

  /**
   * Token object used by the parser.
   *
   * @param type token type
   * @param text token text
   * @param position start position in the original source text
   */
  private record Token(TokenType type, String text, int position) {

    /** Creates one token. */
    private Token {
      type = Objects.requireNonNull(type, "type");
      text = Objects.requireNonNull(text, "text");
    }
  }

  /** Token types for the if-condition parser. */
  private enum TokenType {
    /** Bare atom token (identifier or literal). */
    ATOM,
    /** Comparison operator token. */
    COMPARISON_OPERATOR,
    /** Text operator {@code and}. */
    AND,
    /** Text operator {@code or}. */
    OR,
    /** Left parenthesis token. */
    LEFT_PAREN,
    /** Right parenthesis token. */
    RIGHT_PAREN,
    /** End-of-input marker token. */
    END
  }

  /**
   * Parse exception for invalid condition syntax.
   *
   * @param message human-readable error message
   */
  static final class IfConditionParseException extends Exception {

    /**
     * Creates one parse exception.
     *
     * @param message human-readable error message
     */
    IfConditionParseException(String message) {
      super(message);
    }
  }
}
