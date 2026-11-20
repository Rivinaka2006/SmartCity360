package com.smartcity360.web.energy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MeterIngestionBoundaryTest {
 @Test void allowsOnlyExactCookielessMachineRoute(){assertTrue(MeterIngestionBoundary.isMachineRequest("POST","energy/streams/process",null));assertFalse(MeterIngestionBoundary.isMachineRequest("POST","energy/streams/process","JSESSIONID=x"));assertFalse(MeterIngestionBoundary.isMachineRequest("PUT","energy/streams/process",null));assertFalse(MeterIngestionBoundary.isMachineRequest("POST","energy/streams/process/extra",null));assertFalse(MeterIngestionBoundary.isMachineRequest("POST","energy/streams/demo",null));assertFalse(MeterIngestionBoundary.isMachineRequest("POST","auth/login",null));}
}
