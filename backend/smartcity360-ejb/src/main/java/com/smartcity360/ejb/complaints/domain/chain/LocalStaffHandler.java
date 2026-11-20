package com.smartcity360.ejb.complaints.domain.chain;

public final class LocalStaffHandler extends BaseComplaintHandler {
    @Override
    protected HandlerLevel level() {
        return HandlerLevel.LOCAL_STAFF;
    }
}
