package com.smartcity360.ejb.policies.domain.interpreter;

import java.math.BigDecimal;
import java.util.*;

public final class DslLexer {
    private final DslLimits limits;

    public DslLexer(DslLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    public List<DslToken> tokenize(String source) {
        if (source == null || source.isBlank()) throw error("SOURCE_EMPTY", "DSL source is required", 0);
        if (source.length() > limits.maxSourceLength())
            throw error("SOURCE_LENGTH_LIMIT", "DSL source is too long", limits.maxSourceLength());
        List<DslToken> tokens = new ArrayList<>();
        int current = 0;
        while (current < source.length()) {
            char c = source.charAt(current);
            if (Character.isWhitespace(c)) {
                current++;
                continue;
            }
            int start = current;
            switch (c) {
                case '(' -> {
                    add(tokens, new DslToken(DslToken.Type.LEFT_PAREN, "(", null, start));
                    current++;
                }
                case ')' -> {
                    add(tokens, new DslToken(DslToken.Type.RIGHT_PAREN, ")", null, start));
                    current++;
                }
                case '>' -> {
                    current++;
                    boolean eq = match(source, current, '=');
                    if (eq) current++;
                    add(tokens, token(eq ? DslToken.Type.GREATER_EQUAL : DslToken.Type.GREATER, source, start, current));
                }
                case '<' -> {
                    current++;
                    boolean eq = match(source, current, '=');
                    if (eq) current++;
                    add(tokens, token(eq ? DslToken.Type.LESS_EQUAL : DslToken.Type.LESS, source, start, current));
                }
                case '=' -> {
                    if (!match(source, current + 1, '=')) throw error("INVALID_OPERATOR", "Use == for equality", start);
                    current += 2;
                    add(tokens, token(DslToken.Type.EQUAL, source, start, current));
                }
                case '!' -> {
                    if (!match(source, current + 1, '='))
                        throw error("INVALID_OPERATOR", "Use != for inequality or NOT for negation", start);
                    current += 2;
                    add(tokens, token(DslToken.Type.NOT_EQUAL, source, start, current));
                }
                case '"' -> {
                    current++;
                    StringBuilder value = new StringBuilder();
                    boolean closed = false;
                    while (current < source.length()) {
                        char ch = source.charAt(current++);
                        if (ch == '"') {
                            closed = true;
                            break;
                        }
                        if (ch == '\\') {
                            if (current >= source.length())
                                throw error("UNTERMINATED_STRING", "Unterminated string literal", start);
                            char escaped = source.charAt(current++);
                            value.append(switch (escaped) {
                                case '"' -> '"';
                                case '\\' -> '\\';
                                case 'n' -> '\n';
                                case 'r' -> '\r';
                                case 't' -> '\t';
                                default ->
                                        throw error("INVALID_STRING_ESCAPE", "Unsupported escape sequence", current - 2);
                            });
                        } else if (ch == '\n' || ch == '\r')
                            throw error("UNTERMINATED_STRING", "String literals cannot span lines", start);
                        else value.append(ch);
                    }
                    if (!closed) throw error("UNTERMINATED_STRING", "Unterminated string literal", start);
                    add(tokens, new DslToken(DslToken.Type.STRING, source.substring(start, current), value.toString(), start));
                }
                default -> {
                    if (isNumberStart(source, current)) {
                        current = numberEnd(source, current);
                        String lexeme = source.substring(start, current);
                        try {
                            add(tokens, new DslToken(DslToken.Type.NUMBER, lexeme, new BigDecimal(lexeme), start));
                        } catch (NumberFormatException exception) {
                            throw error("INVALID_NUMBER", "Invalid numeric literal", start);
                        }
                    } else if (Character.isLetter(c) || c == '_') {
                        current++;
                        while (current < source.length() && (Character.isLetterOrDigit(source.charAt(current)) || source.charAt(current) == '_'))
                            current++;
                        String lexeme = source.substring(start, current);
                        String keyword = lexeme.toUpperCase(Locale.ROOT);
                        DslToken token = switch (keyword) {
                            case "AND" -> new DslToken(DslToken.Type.AND, lexeme, null, start);
                            case "OR" -> new DslToken(DslToken.Type.OR, lexeme, null, start);
                            case "NOT" -> new DslToken(DslToken.Type.NOT, lexeme, null, start);
                            case "TRUE" -> new DslToken(DslToken.Type.BOOLEAN, lexeme, Boolean.TRUE, start);
                            case "FALSE" -> new DslToken(DslToken.Type.BOOLEAN, lexeme, Boolean.FALSE, start);
                            default -> new DslToken(DslToken.Type.IDENTIFIER, keyword, null, start);
                        };
                        add(tokens, token);
                    } else throw error("UNEXPECTED_CHARACTER", "Unsupported character '" + c + "'", start);
                }
            }
        }
        add(tokens, new DslToken(DslToken.Type.EOF, "", null, source.length()));
        return List.copyOf(tokens);
    }

    private void add(List<DslToken> tokens, DslToken token) {
        if (tokens.size() >= limits.maxTokens())
            throw error("TOKEN_LIMIT", "DSL token limit exceeded", token.position());
        tokens.add(token);
    }

    private static boolean match(String source, int index, char expected) {
        return index < source.length() && source.charAt(index) == expected;
    }

    private static DslToken token(DslToken.Type type, String source, int start, int end) {
        return new DslToken(type, source.substring(start, end), null, start);
    }

    private static boolean isNumberStart(String source, int index) {
        return Character.isDigit(source.charAt(index)) || (source.charAt(index) == '-' && index + 1 < source.length() && Character.isDigit(source.charAt(index + 1)));
    }

    private static int numberEnd(String source, int index) {
        int current = source.charAt(index) == '-' ? index + 1 : index;
        while (current < source.length() && Character.isDigit(source.charAt(current))) current++;
        if (current < source.length() && source.charAt(current) == '.') {
            current++;
            int fraction = current;
            while (current < source.length() && Character.isDigit(source.charAt(current))) current++;
            if (fraction == current)
                throw error("INVALID_NUMBER", "A decimal point must be followed by digits", current - 1);
        }
        return current;
    }

    private static DslValidationException error(String code, String message, int position) {
        return new DslValidationException(code, message, position);
    }
}
