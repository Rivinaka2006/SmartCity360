package com.smartcity360.web;

import jakarta.ws.rs.ApplicationPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestApplicationTest {

    @Test
    void applicationUsesVersionedApiPath() {
        assertEquals("/api/v1", RestApplication.class.getAnnotation(ApplicationPath.class).value());
    }
}
