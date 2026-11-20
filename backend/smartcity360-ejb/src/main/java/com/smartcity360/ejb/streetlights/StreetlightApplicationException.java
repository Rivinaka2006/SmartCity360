package com.smartcity360.ejb.streetlights;

import com.smartcity360.common.streetlights.StreetlightServiceException;
import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class StreetlightApplicationException extends StreetlightServiceException {
    public StreetlightApplicationException(String code) {
        super(code);
    }
}
