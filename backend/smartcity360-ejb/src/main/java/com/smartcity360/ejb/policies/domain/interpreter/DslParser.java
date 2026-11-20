package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.*;

public final class DslParser {
    private final PolicyVariableRegistry registry;
    private final DslLimits limits;
    private final PolicyDomain domain;
    private List<DslToken> tokens = List.of();
    private int current;
    private int nodes;
    private final Map<Expression, Integer> expressionDepths = new IdentityHashMap<>();

    public DslParser(PolicyVariableRegistry registry, DslLimits limits, PolicyDomain domain) {
        this.registry = Objects.requireNonNull(registry);
        this.limits = Objects.requireNonNull(limits);
        this.domain = Objects.requireNonNull(domain);
    }

    public Expression parse(String source) {
        tokens = new DslLexer(limits).tokenize(source);
        current = 0;
        nodes = 0;
        expressionDepths.clear();
        Expression expression = parseOr(1);
        if (!check(DslToken.Type.EOF))
            throw error("UNEXPECTED_TOKEN", "Unexpected token '" + peek().lexeme() + "'", peek());
        return expression;
    }

    private Expression parseOr(int depth) {
        Expression expression = parseAnd(depth);
        while (match(DslToken.Type.OR)) {
            DslToken operator = previous();
            Expression right = parseAnd(depth);
            expression = binary(new OrExpression(expression, right), expression, right, operator);
        }
        return expression;
    }

    private Expression parseAnd(int depth) {
        Expression expression = parseNot(depth);
        while (match(DslToken.Type.AND)) {
            DslToken operator = previous();
            Expression right = parseNot(depth);
            expression = binary(new AndExpression(expression, right), expression, right, operator);
        }
        return expression;
    }

    private Expression parseNot(int depth) {
        checkDepth(depth);
        if (match(DslToken.Type.NOT)) {
            DslToken operator = previous();
            Expression child = parseNot(depth + 1);
            return node(new NotExpression(child), operator, expressionDepths.get(child) + 1);
        }
        return parsePrimary(depth);
    }

    private Expression parsePrimary(int depth) {
        checkDepth(depth);
        if (match(DslToken.Type.LEFT_PAREN)) {
            Expression expression = parseOr(depth + 1);
            consume(DslToken.Type.RIGHT_PAREN, "EXPECTED_RIGHT_PAREN", "Expected ')' after expression");
            return expression;
        }
        return parseComparison();
    }

    private Expression parseComparison() {
        Operand left = parseOperand();
        DslToken operatorToken = advance();
        ComparisonOperator operator = switch (operatorToken.type()) {
            case GREATER -> ComparisonOperator.GREATER;
            case GREATER_EQUAL -> ComparisonOperator.GREATER_EQUAL;
            case LESS -> ComparisonOperator.LESS;
            case LESS_EQUAL -> ComparisonOperator.LESS_EQUAL;
            case EQUAL -> ComparisonOperator.EQUAL;
            case NOT_EQUAL -> ComparisonOperator.NOT_EQUAL;
            default -> throw error("EXPECTED_COMPARISON_OPERATOR", "Expected a comparison operator", operatorToken);
        };
        Operand right = parseOperand();
        if (left.type() != right.type())
            throw error("TYPE_MISMATCH", "Comparison operands must have the same type", operatorToken);
        if (operator != ComparisonOperator.EQUAL && operator != ComparisonOperator.NOT_EQUAL && left.type() != ValueType.NUMERIC)
            throw error("ORDERING_REQUIRES_NUMERIC", "Ordering comparisons require numeric operands", operatorToken);
        return node(new ComparisonExpression(left, operator, right), operatorToken, 1);
    }

    private Operand parseOperand() {
        DslToken token = advance();
        Operand operand = switch (token.type()) {
            case IDENTIFIER -> {
                PolicyVariable variable = registry.require(token.lexeme(), domain, token.position());
                yield new VariableOperand(PolicyVariableRegistry.normalize(token.lexeme()), variable.type());
            }
            case NUMBER -> new NumericLiteral((BigDecimal) token.literal());
            case STRING -> new StringLiteral((String) token.literal());
            case BOOLEAN -> new BooleanLiteral((Boolean) token.literal());
            default -> throw error("EXPECTED_OPERAND", "Expected a variable or literal", token);
        };
        nodes++;
        checkNodes(token);
        return operand;
    }

    private <T extends Expression> T binary(T expression, Expression left, Expression right, DslToken token) {
        return node(expression, token, Math.max(expressionDepths.get(left), expressionDepths.get(right)) + 1);
    }

    private <T extends Expression> T node(T expression, DslToken token, int astDepth) {
        nodes++;
        checkNodes(token);
        if (astDepth > limits.maxAstDepth()) throw error("AST_DEPTH_LIMIT", "Expression tree is too deep", token);
        expressionDepths.put(expression, astDepth);
        return expression;
    }

    private void checkNodes(DslToken token) {
        if (nodes > limits.maxAstNodes()) throw error("AST_NODE_LIMIT", "Expression tree is too large", token);
    }

    private void checkDepth(int depth) {
        if (depth > limits.maxAstDepth()) throw error("AST_DEPTH_LIMIT", "Expression nesting is too deep", peek());
    }

    private boolean match(DslToken.Type type) {
        if (!check(type)) return false;
        advance();
        return true;
    }

    private DslToken consume(DslToken.Type type, String code, String message) {
        if (check(type)) return advance();
        throw error(code, message, peek());
    }

    private boolean check(DslToken.Type type) {
        return peek().type() == type;
    }

    private DslToken advance() {
        if (current < tokens.size()) current++;
        return previous();
    }

    private DslToken peek() {
        return tokens.get(Math.min(current, tokens.size() - 1));
    }

    private DslToken previous() {
        return tokens.get(Math.max(0, current - 1));
    }

    private static DslValidationException error(String code, String message, DslToken token) {
        return new DslValidationException(code, message, token.position());
    }
}
