package com.smartcity360.ejb.zones.domain.composite;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class BuildingLeaf implements ZoneComponent {
    private final UUID id;
    private final String code;
    private final String name;

    public BuildingLeaf(UUID id, String code, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = requireText(code, "code");
        this.name = requireText(name, "name");
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ZoneKind kind() {
        return ZoneKind.BUILDING;
    }

    @Override
    public List<ZoneComponent> children() {
        return List.of();
    }

    @Override
    public PowerOperationResult shutdown(PowerOperationContext context) {
        return apply(context, PowerState.OFF);
    }

    @Override
    public PowerOperationResult restore(PowerOperationContext context) {
        return apply(context, PowerState.ON);
    }

    @Override
    public UsageReport reportUsage(UsageQuery query) {
        return Objects.requireNonNull(query, "query").source().load(id, query.from(), query.to());
    }

    private PowerOperationResult apply(PowerOperationContext context, PowerState desired) {
        boolean changed = Objects.requireNonNull(context, "context").stateChanger().change(id, desired);
        return new PowerOperationResult(1, changed ? 1 : 0);
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
