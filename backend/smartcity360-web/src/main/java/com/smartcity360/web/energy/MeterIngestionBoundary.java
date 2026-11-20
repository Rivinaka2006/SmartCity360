package com.smartcity360.web.energy;

public final class MeterIngestionBoundary {
    private MeterIngestionBoundary() {
    }

    public static boolean isMachineRequest(String method, String path, String cookie) {
        return "POST".equals(method) && "energy/streams/process".equals(path) && (cookie == null || cookie.isBlank());
    }
}
