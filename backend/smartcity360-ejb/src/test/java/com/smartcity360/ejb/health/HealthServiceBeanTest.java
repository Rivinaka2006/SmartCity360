package com.smartcity360.ejb.health;

import com.smartcity360.common.health.HealthStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthServiceBeanTest {

    @Test
    void currentStatusReportsApplicationUp() {
        HealthStatus status = new HealthServiceBean().currentStatus();

        assertEquals("UP", status.getStatus());
        assertEquals("smartcity360", status.getService());
    }
}
