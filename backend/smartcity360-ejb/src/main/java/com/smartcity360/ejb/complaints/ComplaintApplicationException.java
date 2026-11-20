package com.smartcity360.ejb.complaints;

import com.smartcity360.common.complaints.ComplaintServiceException;
import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public final class ComplaintApplicationException extends ComplaintServiceException {
    public ComplaintApplicationException(String code) {
        super(code);
    }
}
