/*
 * Copyright 2026 The Ground Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.groundplatform.v2.core.forms.xpath.ast

import org.groundplatform.v2.core.forms.xpath.XPathSyntaxException

/**
 * Recursive-descent parser for XPath 1.0 and XForms expressions.
 *
 * Implements the standard XPath 1.0 operator precedence hierarchy:
 * 1. `or` / `||`
 * 2. `and` / `&&`
 * 3. `=` / `!=`
 * 4. `<` / `<=` / `>` / `>=`
 * 5. `+` / `-`
 * 6. `*` / `div` / `mod`
 * 7. Unary `-`
 * 8. Union `|`
 * 9. Path / Filter expressions (`/`, `//`, `[predicate]`)
 */
class XPathParser(private val tokens: List<XPathToken>) {
  private var pos = 0

  companion object {
    fun parse(expression: String): XPathExpr {
      val trimmed = expression.trim()
      if (trimmed.isEmpty()) {
        throw XPathSyntaxException("XPath expression cannot be empty", 0)
      }
      val tokens = XPathLexer(trimmed).tokenize()
      val parser = XPathParser(tokens)
      val expr = parser.parseExpr()
      if (parser.peek().type != TokenType.EOF) {
        val unexpected = parser.peek()
        throw XPathSyntaxException(
          "Unexpected token '${unexpected.text}' after expression",
          unexpected.position,
        )
      }
      return expr
    }
  }

  private fun peek(offset: Int = 0): XPathToken {
    val index = pos + offset
    return if (index < tokens.size) tokens[index] else tokens.last()
  }

  private fun advance(): XPathToken {
    val current = peek()
    if (current.type != TokenType.EOF) {
      pos++
    }
    return current
  }

  private fun match(vararg types: TokenType): Boolean {
    if (types.contains(peek().type)) {
      advance()
      return true
    }
    return false
  }

  private fun expect(type: TokenType, message: String): XPathToken {
    val token = peek()
    if (token.type != type) {
      throw XPathSyntaxException("$message, got '${token.text}'", token.position)
    }
    return advance()
  }

  fun parseExpr(): XPathExpr = parseOrExpr()

  private fun parseOrExpr(): XPathExpr {
    var left = parseAndExpr()
    while (match(TokenType.OR)) {
      val right = parseAndExpr()
      left = XPathExpr.BinaryExpr(BinaryOp.OR, left, right)
    }
    return left
  }

  private fun parseAndExpr(): XPathExpr {
    var left = parseEqualityExpr()
    while (match(TokenType.AND)) {
      val right = parseEqualityExpr()
      left = XPathExpr.BinaryExpr(BinaryOp.AND, left, right)
    }
    return left
  }

  private fun parseEqualityExpr(): XPathExpr {
    var left = parseRelationalExpr()
    while (true) {
      val op =
        when (peek().type) {
          TokenType.EQ -> BinaryOp.EQ
          TokenType.NEQ -> BinaryOp.NEQ
          else -> break
        }
      advance()
      val right = parseRelationalExpr()
      left = XPathExpr.BinaryExpr(op, left, right)
    }
    return left
  }

  private fun parseRelationalExpr(): XPathExpr {
    var left = parseAdditiveExpr()
    while (true) {
      val op =
        when (peek().type) {
          TokenType.LT -> BinaryOp.LT
          TokenType.LTE -> BinaryOp.LTE
          TokenType.GT -> BinaryOp.GT
          TokenType.GTE -> BinaryOp.GTE
          else -> break
        }
      advance()
      val right = parseAdditiveExpr()
      left = XPathExpr.BinaryExpr(op, left, right)
    }
    return left
  }

  private fun parseAdditiveExpr(): XPathExpr {
    var left = parseMultiplicativeExpr()
    while (true) {
      val op =
        when (peek().type) {
          TokenType.PLUS -> BinaryOp.ADD
          TokenType.MINUS -> BinaryOp.SUB
          else -> break
        }
      advance()
      val right = parseMultiplicativeExpr()
      left = XPathExpr.BinaryExpr(op, left, right)
    }
    return left
  }

  private fun parseMultiplicativeExpr(): XPathExpr {
    var left = parseUnaryExpr()
    while (true) {
      val op =
        when (peek().type) {
          TokenType.STAR -> BinaryOp.MUL
          TokenType.DIV -> BinaryOp.DIV
          TokenType.MOD -> BinaryOp.MOD
          else -> break
        }
      advance()
      val right = parseUnaryExpr()
      left = XPathExpr.BinaryExpr(op, left, right)
    }
    return left
  }

  private fun parseUnaryExpr(): XPathExpr {
    if (match(TokenType.MINUS)) {
      return XPathExpr.UnaryMinusExpr(parseUnaryExpr())
    }
    return parseUnionExpr()
  }

  private fun parseUnionExpr(): XPathExpr {
    var left = parsePathExpr()
    while (match(TokenType.PIPE)) {
      val right = parsePathExpr()
      left = XPathExpr.BinaryExpr(BinaryOp.UNION, left, right)
    }
    return left
  }

  private fun parsePathExpr(): XPathExpr {
    val current = peek()
    // Case 1: Starts with '/' or '//' -> Absolute LocationPath
    if (current.type == TokenType.SLASH || current.type == TokenType.DOUBLE_SLASH) {
      return parseLocationPath(isAbsolute = true)
    }

    // Case 2: Determine whether this starts with a PrimaryExpr (literal, number, variable,
    // parenthesized expr, or function call not matching node()/text())
    if (isPrimaryExprStart()) {
      val primary = parsePrimaryExpr()
      val predicates = mutableListOf<XPathExpr>()
      while (peek().type == TokenType.LBRACKET) {
        predicates.add(parsePredicate())
      }
      val trailingSteps = mutableListOf<LocationStep>()
      while (peek().type == TokenType.SLASH || peek().type == TokenType.DOUBLE_SLASH) {
        val isDoubleSlash = peek().type == TokenType.DOUBLE_SLASH
        advance()
        if (isDoubleSlash) {
          trailingSteps.add(
            LocationStep(Axis.DESCENDANT_OR_SELF, NodeTest.AnyNodeTest, emptyList())
          )
        }
        trailingSteps.addAll(parseRelativeLocationSteps())
      }
      return if (predicates.isEmpty() && trailingSteps.isEmpty()) {
        primary
      } else {
        XPathExpr.FilterPathExpr(primary, predicates, trailingSteps)
      }
    }

    // Case 3: Relative LocationPath (e.g., '.', '..', 'person/age', 'child::node')
    return parseLocationPath(isAbsolute = false)
  }

  private fun isPrimaryExprStart(): Boolean {
    val token = peek()
    return when (token.type) {
      TokenType.NUMBER_LITERAL,
      TokenType.STRING_LITERAL,
      TokenType.VARIABLE_REF,
      TokenType.LPAREN -> true
      TokenType.IDENTIFIER -> {
        // Function call if next token is LPAREN AND it is not a NodeType test ('node' or 'text')
        // followed by axis/step syntax, or if it's a standard XPath function
        val next = peek(1)
        next.type == TokenType.LPAREN && token.text != "node" && token.text != "text"
      }
      else -> false
    }
  }

  private fun parsePrimaryExpr(): XPathExpr {
    val token = peek()
    return when (token.type) {
      TokenType.NUMBER_LITERAL -> {
        advance()
        val num =
          token.text.toDoubleOrNull()
            ?: throw XPathSyntaxException("Invalid number literal '${token.text}'", token.position)
        XPathExpr.LiteralNumber(num)
      }
      TokenType.STRING_LITERAL -> {
        advance()
        XPathExpr.LiteralString(token.text)
      }
      TokenType.VARIABLE_REF -> {
        advance()
        XPathExpr.VariableReferenceExpr(token.text)
      }
      TokenType.LPAREN -> {
        advance() // consume '('
        val inner = parseExpr()
        expect(TokenType.RPAREN, "Expected closing ')'")
        inner
      }
      TokenType.IDENTIFIER -> {
        val name = advance().text
        expect(TokenType.LPAREN, "Expected '(' after function name '$name'")
        val args = mutableListOf<XPathExpr>()
        if (peek().type != TokenType.RPAREN) {
          args.add(parseExpr())
          while (match(TokenType.COMMA)) {
            args.add(parseExpr())
          }
        }
        expect(TokenType.RPAREN, "Expected ')' after arguments to function '$name'")
        XPathExpr.FunctionCallExpr(name, args)
      }
      else -> throw XPathSyntaxException("Expected primary expression", token.position)
    }
  }

  private fun parseLocationPath(isAbsolute: Boolean): XPathExpr.LocationPathExpr {
    val steps = mutableListOf<LocationStep>()
    if (isAbsolute) {
      if (match(TokenType.DOUBLE_SLASH)) {
        steps.add(LocationStep(Axis.DESCENDANT_OR_SELF, NodeTest.AnyNodeTest, emptyList()))
        steps.addAll(parseRelativeLocationSteps())
      } else if (match(TokenType.SLASH)) {
        // Bare '/' root path is valid if followed by EOF or non-step operator
        if (canStartStep(peek())) {
          steps.addAll(parseRelativeLocationSteps())
        }
      }
    } else {
      steps.addAll(parseRelativeLocationSteps())
    }
    return XPathExpr.LocationPathExpr(isAbsolute, steps)
  }

  private fun parseRelativeLocationSteps(): List<LocationStep> {
    val steps = mutableListOf<LocationStep>()
    steps.add(parseStep())
    while (peek().type == TokenType.SLASH || peek().type == TokenType.DOUBLE_SLASH) {
      val isDouble = peek().type == TokenType.DOUBLE_SLASH
      advance()
      if (isDouble) {
        steps.add(LocationStep(Axis.DESCENDANT_OR_SELF, NodeTest.AnyNodeTest, emptyList()))
      }
      steps.add(parseStep())
    }
    return steps
  }

  private fun canStartStep(token: XPathToken): Boolean =
    when (token.type) {
      TokenType.DOT,
      TokenType.DOUBLE_DOT,
      TokenType.STAR,
      TokenType.IDENTIFIER -> true
      else -> false
    }

  private fun parseStep(): LocationStep {
    val token = peek()
    if (match(TokenType.DOT)) {
      val preds = parsePredicates()
      return LocationStep(Axis.SELF, NodeTest.AnyNodeTest, preds)
    }
    if (match(TokenType.DOUBLE_DOT)) {
      val preds = parsePredicates()
      return LocationStep(Axis.PARENT, NodeTest.AnyNodeTest, preds)
    }

    var axis = Axis.CHILD
    if (token.type == TokenType.IDENTIFIER && peek(1).type == TokenType.DOUBLE_COLON) {
      val axisName = advance().text
      advance() // consume '::'
      axis = parseAxisName(axisName, token.position)
    }

    val nodeTest = parseNodeTest()
    val preds = parsePredicates()
    return LocationStep(axis, nodeTest, preds)
  }

  private fun parseAxisName(name: String, position: Int): Axis =
    when (name) {
      "self" -> Axis.SELF
      "parent" -> Axis.PARENT
      "child" -> Axis.CHILD
      "descendant" -> Axis.DESCENDANT
      "descendant-or-self" -> Axis.DESCENDANT_OR_SELF
      "ancestor" -> Axis.ANCESTOR
      "ancestor-or-self" -> Axis.ANCESTOR_OR_SELF
      "following-sibling" -> Axis.FOLLOWING_SIBLING
      "preceding-sibling" -> Axis.PRECEDING_SIBLING
      else -> throw XPathSyntaxException("Unsupported XPath axis '$name'", position)
    }

  private fun parseNodeTest(): NodeTest {
    val token = peek()
    return when (token.type) {
      TokenType.STAR -> {
        advance()
        NodeTest.WildcardTest
      }
      TokenType.IDENTIFIER -> {
        val name = advance().text
        if (peek().type == TokenType.LPAREN) {
          advance() // consume '('
          expect(TokenType.RPAREN, "Expected ')' after node type test '$name()'")
          when (name) {
            "node" -> NodeTest.AnyNodeTest
            "text" -> NodeTest.TextNodeTest
            else -> throw XPathSyntaxException("Unknown node type test '$name()'", token.position)
          }
        } else {
          NodeTest.NameTest(name)
        }
      }
      else ->
        throw XPathSyntaxException("Expected node name or '*' in location step", token.position)
    }
  }

  private fun parsePredicates(): List<XPathExpr> {
    val predicates = mutableListOf<XPathExpr>()
    while (peek().type == TokenType.LBRACKET) {
      predicates.add(parsePredicate())
    }
    return predicates
  }

  private fun parsePredicate(): XPathExpr {
    expect(TokenType.LBRACKET, "Expected '['")
    val expr = parseExpr()
    expect(TokenType.RBRACKET, "Expected ']' after predicate expression")
    return expr
  }
}
