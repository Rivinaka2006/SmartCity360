package com.smartcity360.common.users;

public record UserCreateCommand(
        String fullName,
        String email,
        String password,
        String role,
        boolean integrationTestAccount) {
}
