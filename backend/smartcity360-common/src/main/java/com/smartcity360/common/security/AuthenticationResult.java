package com.smartcity360.common.security;
import java.io.Serializable; import java.util.Set; import java.util.UUID;
public record AuthenticationResult(boolean authenticated, UUID userId, String principalName, Set<String> roles) implements Serializable {
 public static AuthenticationResult failed(){return new AuthenticationResult(false,null,null,Set.of());}
 public AuthenticationResult{roles=roles==null?Set.of():Set.copyOf(roles);}
}
