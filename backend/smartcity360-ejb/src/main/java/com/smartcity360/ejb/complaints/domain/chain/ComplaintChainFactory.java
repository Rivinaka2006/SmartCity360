package com.smartcity360.ejb.complaints.domain.chain;

import java.util.EnumSet;
import java.util.List;

public final class ComplaintChainFactory {
    public ComplaintHandler standardChain() {
        return build(List.of(HandlerLevel.LOCAL_STAFF, HandlerLevel.SUPERVISOR, HandlerLevel.CITY_MANAGER));
    }

    public ComplaintHandler build(List<HandlerLevel> orderedLevels) {

        if (orderedLevels == null || orderedLevels.isEmpty())
            throw new IllegalArgumentException("at least one complaint handler is required");
        EnumSet<HandlerLevel> unique = EnumSet.noneOf(HandlerLevel.class);
        BaseComplaintHandler first = null;
        BaseComplaintHandler tail = null;

        for (HandlerLevel level : orderedLevels) {
            if (level == null || !unique.add(level))
                throw new IllegalArgumentException("handler levels must be non-null and unique");
            BaseComplaintHandler next = switch (level) {
                case LOCAL_STAFF -> new LocalStaffHandler();
                case SUPERVISOR -> new SupervisorHandler();
                case CITY_MANAGER -> new CityManagerHandler();
            };
            if (first == null) first = next;
            else tail.setNext(next);
            tail = next;
        }
        return first;
    }
}
