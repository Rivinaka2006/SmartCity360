package com.smartcity360.ejb.zones.domain.composite;

import java.util.List;
import java.util.UUID;

public interface ZoneComponent {
    UUID id();

    String code();

    String name();

    ZoneKind kind();

    List<ZoneComponent> children();

    PowerOperationResult shutdown(PowerOperationContext context);

    PowerOperationResult restore(PowerOperationContext context);

    UsageReport reportUsage(UsageQuery query);
}
