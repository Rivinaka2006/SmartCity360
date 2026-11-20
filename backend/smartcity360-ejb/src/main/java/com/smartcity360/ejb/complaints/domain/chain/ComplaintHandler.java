package com.smartcity360.ejb.complaints.domain.chain;

public interface ComplaintHandler {
    RoutingDecision route(ComplaintRoutingContext context);
}
