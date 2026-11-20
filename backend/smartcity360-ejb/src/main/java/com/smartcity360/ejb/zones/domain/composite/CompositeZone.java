package com.smartcity360.ejb.zones.domain.composite;

import java.util.*;

public final class CompositeZone implements ZoneComponent {
    private final UUID id;
    private final String code;
    private final String name;
    private final ZoneKind kind;
    private final List<ZoneComponent> children = new ArrayList<>();
    private final Set<UUID> childIds = new HashSet<>();

    public CompositeZone(UUID id, String code, String name, ZoneKind kind) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = BuildingLeaf.requireText(code, "code");
        this.name = BuildingLeaf.requireText(name, "name");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (kind == ZoneKind.BUILDING) throw new IllegalArgumentException("BUILDING must be a leaf");
    }

    public CompositeZone add(ZoneComponent child) {
        Objects.requireNonNull(child, "child");
        if (child.kind() != kind.childKind()) throw new IllegalArgumentException("invalid child type");
        if (!childIds.add(child.id())) throw new IllegalArgumentException("duplicate child");
        children.add(child);
        children.sort(Comparator.comparing(ZoneComponent::code).thenComparing(ZoneComponent::id));
        return this;
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
        return kind;
    }

    @Override
    public List<ZoneComponent> children() {
        return List.copyOf(children);
    }

    @Override
    public PowerOperationResult shutdown(PowerOperationContext context) {
        return power(context, false);
    }

    @Override
    public PowerOperationResult restore(PowerOperationContext context) {
        return power(context, true);
    }

    @Override
    public UsageReport reportUsage(UsageQuery query) {
        UsageReport result = UsageReport.empty(query.from(), query.to());
        for (ZoneComponent child : children) result = result.plus(child.reportUsage(query));
        return result;
    }

    private PowerOperationResult power(PowerOperationContext context, boolean restore) {
        PowerOperationResult result = PowerOperationResult.NONE;
        for (ZoneComponent child : children) {
            result = result.plus(restore ? child.restore(context) : child.shutdown(context));
        }
        return result;
    }
}
