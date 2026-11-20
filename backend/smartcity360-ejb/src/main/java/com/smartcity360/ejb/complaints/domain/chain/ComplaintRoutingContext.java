package com.smartcity360.ejb.complaints.domain.chain;

import java.util.Objects;

public record ComplaintRoutingContext(ComplaintSeverity severity, HandlerLevel minimumLevel) {
    public ComplaintRoutingContext {
        Objects.requireNonNull(severity);
        Objects.requireNonNull(minimumLevel);
    }

    public static ComplaintRoutingContext initial(ComplaintSeverity severity) {
        return new ComplaintRoutingContext(severity, HandlerLevel.LOCAL_STAFF);
    }

    public static ComplaintRoutingContext escalating(ComplaintSeverity severity, HandlerLevel minimum) {
        return new ComplaintRoutingContext(severity, minimum);
    }

    public HandlerLevel targetLevel() {
        HandlerLevel severityLevel = switch (severity) {
            case LOW, MEDIUM -> HandlerLevel.LOCAL_STAFF;
            case HIGH -> HandlerLevel.SUPERVISOR;
            case CRITICAL -> HandlerLevel.CITY_MANAGER;
        };
        return severityLevel.ordinal() >= minimumLevel.ordinal() ? severityLevel : minimumLevel;
    }
}
