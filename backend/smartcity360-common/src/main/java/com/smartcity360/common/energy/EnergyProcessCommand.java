package com.smartcity360.common.energy;
import java.io.Serializable; import java.time.Instant; import java.util.List;
public record EnergyProcessCommand(String meterCode,int credentialVersion,Instant timestamp,String nonce,String method,String path,byte[] signature,byte[] payload,List<String> layers) implements Serializable {
 public EnergyProcessCommand { signature=signature==null?null:signature.clone();payload=payload==null?null:payload.clone();layers=layers==null?List.of():List.copyOf(layers); }
 @Override public byte[] signature(){return signature==null?null:signature.clone();} @Override public byte[] payload(){return payload==null?null:payload.clone();}
}
