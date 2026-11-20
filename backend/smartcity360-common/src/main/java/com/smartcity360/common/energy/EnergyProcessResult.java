package com.smartcity360.common.energy;
import java.io.Serializable; import java.util.*;
public record EnergyProcessResult(UUID runId,byte[] output,List<String> layers,int inputBytes,int outputBytes) implements Serializable { public EnergyProcessResult{output=output.clone();layers=List.copyOf(layers);}@Override public byte[] output(){return output.clone();} }
