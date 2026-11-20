package com.smartcity360.ejb.policies.domain.interpreter;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DslInterpreterTest {
    private final DslLimits limits = DslLimits.defaults();
    private final PolicyVariableRegistry registry = PolicyVariableRegistry.standard();

    @Test
    void lexerRecognizesKeywordsOperatorsLiteralsAndPositions() {
        var tokens = new DslLexer(limits).tokenize("NOISE_DB >= 65 AND ZONE == \"A\\\"1\" AND IS_WEEKEND == true");

        assertEquals(DslToken.Type.IDENTIFIER, tokens.get(0).type());
        assertEquals(DslToken.Type.GREATER_EQUAL, tokens.get(1).type());
        assertEquals(new BigDecimal("65"), tokens.get(2).literal());
        assertEquals("A\"1", tokens.get(6).literal());
        assertEquals(Boolean.TRUE, tokens.get(10).literal());
        assertEquals(DslToken.Type.EOF, tokens.get(tokens.size() - 1).type());
        assertEquals(0, tokens.get(0).position());
    }

    @Test
    void parserAppliesComparisonThenNotThenAndThenOrPrecedence() {
        Expression expression = parser(PolicyDomain.GENERAL).parse(
                "NOT NOISE_DB > 65 OR HOUR >= 22 AND IS_WEEKEND == true");

        EvaluationResult result = expression.interpret(context(PolicyDomain.GENERAL, Map.of(
                "NOISE_DB", new BigDecimal("70"), "HOUR", 23, "IS_WEEKEND", true)));

        assertTrue(result.outcome());
        assertEquals("OR", result.explanation().operator());
        assertTrue(result.explanation().children().get(1).evaluated());
    }

    @Test
    void parenthesesOverridePrecedenceAndStringComparisonIsCaseSensitive() {
        Expression expression = parser(PolicyDomain.PARKING).parse(
                "(PARKING_MINUTES > 120 OR HOUR >= 20) AND ZONE == \"A\"");

        assertTrue(expression.interpret(context(PolicyDomain.PARKING, Map.of(
                "PARKING_MINUTES", 90, "HOUR", 21, "ZONE", "A"))).outcome());
        assertFalse(expression.interpret(context(PolicyDomain.PARKING, Map.of(
                "PARKING_MINUTES", 150, "HOUR", 10, "ZONE", "a"))).outcome());
    }

    @Test
    void logicalExpressionsShortCircuitAndExplainSkippedBranch() {
        Expression expression = parser(PolicyDomain.GENERAL).parse(
                "HOUR == 22 OR PARKING_MINUTES > 10");

        EvaluationResult result = expression.interpret(context(PolicyDomain.GENERAL,
                Map.of("HOUR", 22)));

        assertTrue(result.outcome());
        assertFalse(result.explanation().children().get(1).evaluated());
    }

    @Test
    void domainRegistryRejectsUnknownAndCrossDomainVariables() {
        DslValidationException unknown = assertThrows(DslValidationException.class,
                () -> parser(PolicyDomain.NOISE).parse("TEMPERATURE > 20"));
        DslValidationException wrongDomain = assertThrows(DslValidationException.class,
                () -> parser(PolicyDomain.NOISE).parse("PARKING_MINUTES > 20"));

        assertEquals("UNKNOWN_VARIABLE", unknown.code());
        assertEquals("VARIABLE_NOT_ALLOWED_FOR_DOMAIN", wrongDomain.code());
    }

    @Test
    void parserRejectsInvalidSyntaxTypesOperatorsAndTrailingTokens() {
        assertCode("EXPECTED_OPERAND", () -> parser(PolicyDomain.NOISE).parse("NOISE_DB >"));
        assertCode("TYPE_MISMATCH", () -> parser(PolicyDomain.NOISE).parse("NOISE_DB == \"loud\""));
        assertCode("ORDERING_REQUIRES_NUMERIC", () -> parser(PolicyDomain.PARKING).parse("ZONE > \"A\""));
        assertCode("EXPECTED_COMPARISON_OPERATOR", () -> parser(PolicyDomain.NOISE).parse("NOISE_DB AND HOUR > 2"));
        assertCode("UNEXPECTED_TOKEN", () -> parser(PolicyDomain.NOISE).parse("NOISE_DB > 10 20"));
        assertCode("UNTERMINATED_STRING", () -> parser(PolicyDomain.PARKING).parse("ZONE == \"A"));
        assertCode("INVALID_OPERATOR", () -> parser(PolicyDomain.NOISE).parse("NOISE_DB = 20"));
    }

    @Test
    void contextValidatesMissingValuesTypesRangesAndSize() {
        Expression expression = parser(PolicyDomain.NOISE).parse("NOISE_DB > 65 AND HOUR >= 22");
        assertCode("MISSING_CONTEXT_VALUE", () -> expression.interpret(
                context(PolicyDomain.NOISE, Map.of("NOISE_DB", 70))));
        assertCode("CONTEXT_TYPE_MISMATCH", () -> context(PolicyDomain.NOISE,
                Map.of("HOUR", "22")));
        assertCode("CONTEXT_VALUE_OUT_OF_RANGE", () -> context(PolicyDomain.NOISE,
                Map.of("HOUR", 24)));

        Map<String, Object> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 17; i++) tooMany.put("X" + i, i);
        assertCode("CONTEXT_ENTRY_LIMIT", () -> context(PolicyDomain.GENERAL, tooMany));
    }

    @Test
    void configurableSourceTokenNodeDepthAndPayloadLimitsAreEnforced() {
        DslLimits tinySource = new DslLimits(10, 256, 256, 32, 16, 8192);
        assertCode("SOURCE_LENGTH_LIMIT", () -> new DslParser(registry, tinySource, PolicyDomain.NOISE)
                .parse("NOISE_DB > 1"));

        DslLimits tinyTokens = new DslLimits(2048, 3, 256, 32, 16, 8192);
        assertCode("TOKEN_LIMIT", () -> new DslParser(registry, tinyTokens, PolicyDomain.NOISE)
                .parse("NOISE_DB > 1"));

        DslLimits tinyNodes = new DslLimits(2048, 256, 2, 32, 16, 8192);
        assertCode("AST_NODE_LIMIT", () -> new DslParser(registry, tinyNodes, PolicyDomain.NOISE)
                .parse("NOISE_DB > 1"));

        DslLimits tinyDepth = new DslLimits(2048, 256, 256, 2, 16, 8192);
        assertCode("AST_DEPTH_LIMIT", () -> new DslParser(registry, tinyDepth, PolicyDomain.NOISE)
                .parse("((NOISE_DB > 1))"));

        DslLimits tinyPayload = new DslLimits(2048, 256, 256, 32, 16, 8);
        assertCode("CONTEXT_PAYLOAD_LIMIT", () -> EvaluationContext.create(PolicyDomain.PARKING,
                Map.of("ZONE", "a-long-zone"), registry, tinyPayload));
    }

    @Test
    void astDepthLimitIncludesLeftDeepLogicalComposition() {
        DslLimits shallow = new DslLimits(2048, 256, 256, 3, 16, 8192);
        assertCode("AST_DEPTH_LIMIT", () -> new DslParser(registry, shallow, PolicyDomain.NOISE)
                .parse("HOUR == 1 AND HOUR == 2 AND HOUR == 3 AND HOUR == 4"));
    }

    @Test
    void contextRejectsCaseInsensitiveDuplicateVariableNames() {
        Map<String, Object> ambiguous = new LinkedHashMap<>();
        ambiguous.put("HOUR", 20);
        ambiguous.put("hour", 21);
        assertCode("DUPLICATE_CONTEXT_VARIABLE", () -> context(PolicyDomain.GENERAL, ambiguous));
    }

    @Test
    void expressionTreeAndContextAreImmutableAndEvaluationIsDeterministic() {
        Map<String, Object> mutable = new LinkedHashMap<>();
        mutable.put("NOISE_DB", new BigDecimal("66.50"));
        EvaluationContext context = context(PolicyDomain.NOISE, mutable);
        mutable.put("NOISE_DB", BigDecimal.ZERO);
        Expression expression = parser(PolicyDomain.NOISE).parse("NOISE_DB > 65");

        assertTrue(expression.interpret(context).outcome());
        assertEquals(expression.interpret(context), expression.interpret(context));
        assertThrows(UnsupportedOperationException.class,
                () -> context.values().put("HOUR", TypedValue.numeric(BigDecimal.ONE)));
        assertTrue(expression.getClass().isRecord());
    }

    private DslParser parser(PolicyDomain domain) {
        return new DslParser(registry, limits, domain);
    }

    private EvaluationContext context(PolicyDomain domain, Map<String, Object> values) {
        return EvaluationContext.create(domain, values, registry, limits);
    }

    private static void assertCode(String code, Executable executable) {
        assertEquals(code, assertThrows(DslValidationException.class, executable::run).code());
    }

    @FunctionalInterface
    private interface Executable { void run(); }
}
