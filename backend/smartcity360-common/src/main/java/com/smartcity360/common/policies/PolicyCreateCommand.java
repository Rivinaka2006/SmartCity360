package com.smartcity360.common.policies;

public record PolicyCreateCommand(String policyCode, String name, String domain, String description) { }
