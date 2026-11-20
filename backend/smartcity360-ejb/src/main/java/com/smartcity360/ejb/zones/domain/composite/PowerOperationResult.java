package com.smartcity360.ejb.zones.domain.composite;

public record PowerOperationResult(int targetedBuildings, int changedBuildings) {
    public static final PowerOperationResult NONE = new PowerOperationResult(0, 0);

    public PowerOperationResult {
        if (targetedBuildings < 0 || changedBuildings < 0 || changedBuildings > targetedBuildings) {
            throw new IllegalArgumentException("invalid power-operation counts");
        }
    }

    public PowerOperationResult plus(PowerOperationResult other) {
        return new PowerOperationResult(targetedBuildings + other.targetedBuildings,
                changedBuildings + other.changedBuildings);
    }
}
