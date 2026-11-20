package com.smartcity360.ejb.policies;

import com.smartcity360.common.policies.PolicyServiceException;
import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public final class PolicyApplicationException extends PolicyServiceException {
    public PolicyApplicationException(String code) {
        super(code);
    }
}
