package com.smartcity360.common.energy;
import java.io.Serializable; import java.time.OffsetDateTime; import java.util.UUID;
public record EnergyRunView(UUID id,String meterCode,String decoratorChain,int inputBytes,Integer outputBytes,String status,Integer durationMs,String errorCode,OffsetDateTime executedAt) implements Serializable {}
