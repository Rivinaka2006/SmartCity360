package com.smartcity360.common.security;
public interface AuthenticationGateway { AuthenticationResult authenticate(String email,char[] password); void recordRejectedAttempt(String identifier); void recordAuthorizationDenied(String identifier); }
