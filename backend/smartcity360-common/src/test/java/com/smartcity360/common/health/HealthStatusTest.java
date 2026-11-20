package com.smartcity360.common.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthStatusTest {

    @Test
    void supportsPortableJsonBindingBeanAccess() {
        HealthStatus status = new HealthStatus();

        status.setStatus("UP");
        status.setService("smartcity360");

        assertEquals("UP", status.getStatus());
        assertEquals("smartcity360", status.getService());
    }
}
