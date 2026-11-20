package com.smartcity360.ejb.complaints.domain.chain;

import java.util.*;

public abstract class BaseComplaintHandler implements ComplaintHandler {
    private BaseComplaintHandler next;

    public BaseComplaintHandler setNext(BaseComplaintHandler candidate) {
        Objects.requireNonNull(candidate);
        if (candidate == this || candidate.contains(this)) throw new IllegalArgumentException("handler cycle");
        next = candidate;
        return candidate;
    }

    @Override
    public final RoutingDecision route(ComplaintRoutingContext context) {
        return route(context, new ArrayList<>(), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private RoutingDecision route(ComplaintRoutingContext context, List<HandlerLevel> trace, Set<BaseComplaintHandler> visited) {
        if (!visited.add(this)) throw new IllegalStateException("handler cycle");
        trace.add(level());
        if (canHandle(context)) return RoutingDecision.handled(level(), trace);
        return next == null ? RoutingDecision.unhandled(trace) : next.route(context, trace, visited);
    }

    private boolean contains(BaseComplaintHandler target) {
        for (BaseComplaintHandler cursor = this; cursor != null; cursor = cursor.next) {
            if (cursor == target) return true;
        }
        return false;
    }

    protected boolean canHandle(ComplaintRoutingContext context) {
        return context.targetLevel() == level();
    }

    protected abstract HandlerLevel level();
}
