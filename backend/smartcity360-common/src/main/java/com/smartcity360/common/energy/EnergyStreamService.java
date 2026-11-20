package com.smartcity360.common.energy;

import java.util.List;
import java.util.UUID;

public interface EnergyStreamService {
    EnergyProcessResult process(EnergyProcessCommand command) throws EnergyStreamServiceException;

    EnergyStreamDemoResult demonstrate(String consumptionKwh) throws EnergyStreamServiceException;

    boolean integratedDemoPrepared();

    EnergyStreamIntegratedDemoResult processIntegratedDemoReading(String consumptionKwh)
            throws EnergyStreamServiceException;

    byte[] verify(byte[] envelope) throws EnergyStreamServiceException;

    List<EnergyRunView> listRuns(int offset, int limit);

    EnergyRunView findRun(UUID id) throws EnergyStreamServiceException;
}
