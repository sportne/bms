package io.github.sportne.bms.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sportne.bms.model.IfComparisonOperator;
import io.github.sportne.bms.model.IfLogicalOperator;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for parsing text {@code if} expressions.
 *
 * <p>These tests document the small expression grammar used by semantic resolution.
 */
class IfConditionExpressionParserTest {

  /** Contract: parser supports comparison expressions with decimal and hex literal forms. */
  @Test
  void parseSupportsNumericComparisonLiterals() throws Exception {
    IfConditionExpressionParser.ParsedCondition decimal =
        IfConditionExpressionParser.parse("version >= 10");
    IfConditionExpressionParser.ParsedCondition hex =
        IfConditionExpressionParser.parse("version == 0x0A");

    assertInstanceOf(IfConditionExpressionParser.ParsedComparisonCondition.class, decimal);
    assertInstanceOf(IfConditionExpressionParser.ParsedComparisonCondition.class, hex);

    IfConditionExpressionParser.ParsedComparisonCondition decimalComparison =
        (IfConditionExpressionParser.ParsedComparisonCondition) decimal;
    IfConditionExpressionParser.ParsedComparisonCondition hexComparison =
        (IfConditionExpressionParser.ParsedComparisonCondition) hex;

    assertEquals(IfComparisonOperator.GTE, decimalComparison.operator());
    assertEquals(new BigInteger("10"), decimalComparison.literal());
    assertEquals(IfComparisonOperator.EQ, hexComparison.operator());
    assertEquals(new BigInteger("10"), hexComparison.literal());
  }

  /** Contract: parser enforces `and` precedence over `or` when no parentheses are present. */
  @Test
  void parseUsesAndBeforeOrPrecedence() throws Exception {
    IfConditionExpressionParser.ParsedCondition condition =
        IfConditionExpressionParser.parse("a == 1 or b == 2 and c == 3");

    assertInstanceOf(IfConditionExpressionParser.ParsedLogicalCondition.class, condition);
    IfConditionExpressionParser.ParsedLogicalCondition root =
        (IfConditionExpressionParser.ParsedLogicalCondition) condition;
    assertEquals(IfLogicalOperator.OR, root.operator());
    assertInstanceOf(IfConditionExpressionParser.ParsedComparisonCondition.class, root.left());
    assertInstanceOf(IfConditionExpressionParser.ParsedLogicalCondition.class, root.right());
    IfConditionExpressionParser.ParsedLogicalCondition right =
        (IfConditionExpressionParser.ParsedLogicalCondition) root.right();
    assertEquals(IfLogicalOperator.AND, right.operator());
  }

  /** Contract: parentheses override default precedence in parsed condition trees. */
  @Test
  void parseRespectsParentheses() throws Exception {
    IfConditionExpressionParser.ParsedCondition condition =
        IfConditionExpressionParser.parse("(a == 1 or b == 2) and c == 3");

    assertInstanceOf(IfConditionExpressionParser.ParsedLogicalCondition.class, condition);
    IfConditionExpressionParser.ParsedLogicalCondition root =
        (IfConditionExpressionParser.ParsedLogicalCondition) condition;
    assertEquals(IfLogicalOperator.AND, root.operator());
    assertInstanceOf(IfConditionExpressionParser.ParsedLogicalCondition.class, root.left());
    IfConditionExpressionParser.ParsedLogicalCondition left =
        (IfConditionExpressionParser.ParsedLogicalCondition) root.left();
    assertEquals(IfLogicalOperator.OR, left.operator());
    assertInstanceOf(IfConditionExpressionParser.ParsedComparisonCondition.class, root.right());
  }

  /** Contract: legacy symbolic logical operators are rejected with migration guidance. */
  @Test
  void parseRejectsLegacyLogicalSymbols() {
    IfConditionExpressionParser.IfConditionParseException exception =
        assertThrows(
            IfConditionExpressionParser.IfConditionParseException.class,
            () -> IfConditionExpressionParser.parse("a == 1 && b == 2"));

    assertTrue(exception.getMessage().contains("Use 'and' and 'or' instead"));

    IfConditionExpressionParser.IfConditionParseException orException =
        assertThrows(
            IfConditionExpressionParser.IfConditionParseException.class,
            () -> IfConditionExpressionParser.parse("a == 1 || b == 2"));
    assertTrue(orException.getMessage().contains("Use 'and' and 'or' instead"));
  }

  /** Contract: non-identifier field names are rejected. */
  @Test
  void parseRejectsInvalidFieldIdentifier() {
    IfConditionExpressionParser.IfConditionParseException exception =
        assertThrows(
            IfConditionExpressionParser.IfConditionParseException.class,
            () -> IfConditionExpressionParser.parse("1bad == 1"));

    assertTrue(exception.getMessage().contains("not a valid identifier"));
  }

  /** Contract: malformed expressions surface clear syntax diagnostics. */
  @Test
  void parseRejectsMalformedExpression() {
    IfConditionExpressionParser.IfConditionParseException exception =
        assertThrows(
            IfConditionExpressionParser.IfConditionParseException.class,
            () -> IfConditionExpressionParser.parse("a == 1 and (b == 2"));

    assertTrue(exception.getMessage().contains("Missing ')'"));
  }
}
