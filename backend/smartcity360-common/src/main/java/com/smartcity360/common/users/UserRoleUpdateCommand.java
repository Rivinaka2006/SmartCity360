package com.smartcity360.common.users;

import java.util.Set;

public record UserRoleUpdateCommand(Set<String> roles) {
}
