package com.smartcity360.common.security;
import java.io.Serializable; import java.util.Set; import java.util.UUID;
public record CurrentUserView(UUID id,String displayName,String email,Set<String> roles) implements Serializable { public CurrentUserView{roles=Set.copyOf(roles);} }
