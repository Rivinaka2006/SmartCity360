package com.smartcity360.ejb.zones;

import com.smartcity360.common.zones.ZoneServiceException;
import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public final class ZoneApplicationException extends ZoneServiceException {
    public ZoneApplicationException(String code) {
        super(code);
    }
}
