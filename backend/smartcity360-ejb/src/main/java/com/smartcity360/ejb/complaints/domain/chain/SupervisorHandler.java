package com.smartcity360.ejb.complaints.domain.chain;

public final class SupervisorHandler extends BaseComplaintHandler {
    @Override
    protected HandlerLevel level() {
        return HandlerLevel.SUPERVISOR;
    }
}
