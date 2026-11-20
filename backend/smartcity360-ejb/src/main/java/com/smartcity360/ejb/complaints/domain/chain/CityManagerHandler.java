package com.smartcity360.ejb.complaints.domain.chain;

public final class CityManagerHandler extends BaseComplaintHandler {
    @Override
    protected HandlerLevel level() {
        return HandlerLevel.CITY_MANAGER;
    }
}
