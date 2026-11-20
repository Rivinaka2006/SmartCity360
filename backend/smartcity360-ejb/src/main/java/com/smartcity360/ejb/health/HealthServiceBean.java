package com.smartcity360.ejb.health;

import com.smartcity360.common.health.HealthService;
import com.smartcity360.common.health.HealthStatus;
import jakarta.ejb.Local;
import jakarta.ejb.Stateless;

@Stateless(name = "HealthServiceBean")
@Local(HealthService.class)
public class HealthServiceBean implements HealthService {

    @Override
    public HealthStatus currentStatus() {
        return new HealthStatus("UP", "smartcity360");
    }
}
