package com.smartcity360.ejb.policies.domain.interpreter;

public record DslLimits(int maxSourceLength, int maxTokens, int maxAstNodes, int maxAstDepth,
                        int maxContextEntries, int maxContextPayloadBytes) {
    public DslLimits {
        if (maxSourceLength < 1 || maxTokens < 1 || maxAstNodes < 1 || maxAstDepth < 1
                || maxContextEntries < 1 || maxContextPayloadBytes < 1) {
            throw new IllegalArgumentException("DSL limits must be positive");
        }
    }

    public static DslLimits defaults() {
        return new DslLimits(2048, 256, 256, 32, 16, 8192);
    }
}
