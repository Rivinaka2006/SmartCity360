package com.smartcity360.common.security;
public interface CallerSecurityService { CurrentUserView currentUser(); void recordLogout(); String adminProbe(); boolean isCallerInRole(String role); }
