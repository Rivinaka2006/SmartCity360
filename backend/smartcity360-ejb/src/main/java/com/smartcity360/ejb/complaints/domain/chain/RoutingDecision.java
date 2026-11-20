package com.smartcity360.ejb.complaints.domain.chain;

import java.util.List;

public record RoutingDecision(boolean handled, HandlerLevel selectedLevel, List<HandlerLevel> traversedLevels) {

    public RoutingDecision {
        traversedLevels = List.copyOf(traversedLevels);
        if (handled != (selectedLevel != null)) throw new IllegalArgumentException("decision mismatch");
    }

    public static RoutingDecision handled(HandlerLevel level, List<HandlerLevel> trace) {
        return new RoutingDecision(true, level, trace);
    }

    public static RoutingDecision unhandled(List<HandlerLevel> trace) {
        return new RoutingDecision(false, null, trace);
    }
}
